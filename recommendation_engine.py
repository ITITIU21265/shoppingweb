"""Build the clothing_shop recommendation cache as a standalone batch service.

Install requirements.txt, then set DB_USER and DB_PASSWORD in the environment.
Optional: DB_HOST (127.0.0.1), DB_PORT (3306), DB_NAME (clothing_shop).
Run: python recommendation_engine.py [--dry-run]

The existing product_recommendations table must already exist. No schema changes
are made. A dry run reads MySQL and builds recommendations without changing data.

Purchases are implicit feedback. Surprise SVD expects explicit numeric ratings,
so this pipeline uses log-scaled purchase counts as surrogate ratings; it is not
an implicit-feedback objective and does not treat unobserved purchases as dislikes.

The cache is replaced with DELETE plus bulk INSERT in one InnoDB transaction.
Readers using normal READ COMMITTED or REPEATABLE READ snapshots see either the
old cache or the fully committed replacement. All model computation finishes
before this transaction begins.
"""

from __future__ import annotations

import argparse
import logging
import os
from contextlib import closing
from typing import Any

import mysql.connector
import numpy as np
import pandas as pd
from scipy.sparse import csr_matrix, hstack
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.neighbors import NearestNeighbors
from sklearn.preprocessing import normalize
from surprise import SVD, Dataset, Reader


LOGGER = logging.getLogger(__name__)
CONTENT_WEIGHT = 0.3
COLLABORATIVE_WEIGHT = 0.7
LATENT_FACTORS = 50
TOP_K = 4
QUERY_BATCH_SIZE = 256
INSERT_BATCH_SIZE = 1000
RANDOM_SEED = 42

# Native Python scalars keep Connector/Python independent of NumPy scalar types.
Recommendation = tuple[int, int, float, int]

INSERT_RECOMMENDATIONS_SQL = """
    INSERT INTO product_recommendations
        (target_product_id, recommended_product_id, score, rank_order)
    VALUES (%s, %s, %s, %s)
"""


def database_config() -> dict[str, Any]:
    """Read credentials without storing or logging passwords in source code."""
    missing = [name for name in ("DB_USER", "DB_PASSWORD") if name not in os.environ]
    if missing:
        raise ValueError("Missing environment variables: " + ", ".join(missing))
    if not os.environ["DB_USER"].strip():
        raise ValueError("DB_USER must not be empty.")
    try:
        port = int(os.environ.get("DB_PORT", "3306"))
    except ValueError as exc:
        raise ValueError("DB_PORT must be an integer.") from exc
    if not 1 <= port <= 65535:
        raise ValueError("DB_PORT must be between 1 and 65535.")
    return {
        "host": os.environ.get("DB_HOST", "127.0.0.1"),
        "port": port,
        "user": os.environ["DB_USER"],
        "password": os.environ["DB_PASSWORD"],
        "database": os.environ.get("DB_NAME", "clothing_shop"),
        "charset": "utf8mb4",
        "connection_timeout": 15,
        "autocommit": False,
    }


def extract_data(config: dict[str, Any]) -> tuple[pd.DataFrame, pd.DataFrame]:
    """Read active candidates and non-cancelled purchases in one snapshot."""
    with closing(mysql.connector.connect(**config)) as connection:
        connection.start_transaction(
            isolation_level="REPEATABLE READ", consistent_snapshot=True, readonly=True
        )
        with closing(connection.cursor()) as cursor:
            cursor.execute(
                """
                SELECT id, name, description, status
                FROM products
                WHERE status = %s AND active = 1
                ORDER BY id
                """,
                ("ACTIVE",),
            )
            products = pd.DataFrame(
                cursor.fetchall(), columns=["id", "name", "description", "status"]
            )
            # DISTINCT counts a product once per order even when several variants
            # occur in that order. No unprovided rating/quantity columns are used.
            cursor.execute(
                """
                SELECT DISTINCT o.id AS order_id, o.user_id, oi.product_id
                FROM order_items AS oi
                INNER JOIN orders AS o ON o.id = oi.order_id
                WHERE o.user_id IS NOT NULL AND oi.product_id IS NOT NULL
                  AND o.status != 'CANCELLED'
                ORDER BY o.user_id, oi.product_id, o.id
                """
            )
            purchases = pd.DataFrame(
                cursor.fetchall(), columns=["order_id", "user_id", "product_id"]
            )
        connection.commit()
    LOGGER.info("Loaded %d active products and %d purchases.", len(products), len(purchases))
    return products, purchases


def build_content_features(products: pd.DataFrame) -> csr_matrix:
    """TF-IDF emphasizes descriptive words that distinguish products."""
    documents = (
        products["name"].fillna("").astype(str)
        + " "
        + products["description"].fillna("").astype(str)
    )
    if products.empty:
        return csr_matrix((0, 1), dtype=np.float64)
    # Unicode tokens retain Vietnamese text; no English-only stop-word removal.
    # L2 normalization puts every nonempty content vector on the same scale.
    vectorizer = TfidfVectorizer(
        lowercase=True, token_pattern=r"(?u)\b\w+\b", norm="l2", dtype=np.float64
    )
    try:
        features = vectorizer.fit_transform(documents).tocsr()
    except ValueError as exc:
        if "empty vocabulary" not in str(exc).lower():
            raise
        LOGGER.warning("No usable product text; using zero content vectors.")
        features = csr_matrix((len(products), 1), dtype=np.float64)
    LOGGER.info("TF-IDF feature matrix: %s.", features.shape)
    return features


def build_collaborative_features(
    products: pd.DataFrame, purchases: pd.DataFrame
) -> np.ndarray:
    """SVD learns compact user/item factors from observed purchase strengths."""
    # Row order must match products and the TF-IDF matrix, never SVD's inner IDs.
    item_features = np.zeros((len(products), LATENT_FACTORS), dtype=np.float64)
    if products.empty or purchases.empty:
        LOGGER.info("No purchase training data; SVD item features remain zero.")
        return item_features

    observations = purchases.dropna(subset=["order_id", "user_id", "product_id"])
    observations = observations.drop_duplicates(["order_id", "user_id", "product_id"])
    if observations.empty:
        LOGGER.warning("No valid purchase observations; SVD item features remain zero.")
        return item_features

    ratings = observations.groupby(["user_id", "product_id"], sort=True).size()
    ratings = ratings.rename("purchase_count").reset_index()
    # A single purchase maps to 1; repeat purchases grow logarithmically so heavy
    # buyers do not dominate. 'rating' is derived in memory, not a database column.
    ratings["rating"] = 1.0 + np.log(ratings["purchase_count"].to_numpy(dtype=np.float64))
    for column in ("user_id", "product_id"):
        ratings[column] = ratings[column].map(lambda value: str(int(value)))
    reader = Reader(rating_scale=(1.0, max(2.0, float(ratings["rating"].max()))))
    trainset = Dataset.load_from_df(
        ratings[["user_id", "product_id", "rating"]], reader
    ).build_full_trainset()
    # Disabling rating biases makes the latent factors learn the purchase signal
    # instead of allowing a global rating mean to explain all one-purchase pairs.
    model = SVD(
        n_factors=LATENT_FACTORS, n_epochs=30, biased=False, random_state=RANDOM_SEED
    )
    model.fit(trainset)

    cold_start_count = 0
    for row_index, product_id in enumerate(products["id"]):
        try:
            inner_id = trainset.to_inner_iid(str(int(product_id)))
        except ValueError:
            # Surprise raises for an unseen raw ID. Its preallocated zero row
            # deliberately leaves this product eligible for content-only matching.
            cold_start_count += 1
            continue
        item_features[row_index] = model.qi[inner_id]
    LOGGER.info(
        "SVD trained on %d user/product pairs; %d active products have no history.",
        len(ratings), cold_start_count,
    )
    return item_features


def build_recommendations(
    products: pd.DataFrame, purchases: pd.DataFrame
) -> list[Recommendation]:
    """Concatenate weighted features, then use cosine KNN for item similarity."""
    product_ids = [int(product_id) for product_id in products["id"]]
    product_count = len(product_ids)
    if product_count < 2:
        LOGGER.info("Fewer than two active products; the new cache will be empty.")
        return []

    content = build_content_features(products)
    collaborative = build_collaborative_features(products, purchases)
    # Normalize each SVD row to make block scales comparable. Zero rows stay zero.
    collaborative = normalize(collaborative, norm="l2", axis=1)
    # These are feature multipliers, exactly 0.3 and 0.7. Cosine on concatenated
    # vectors is not equivalent to a 0.3/0.7 weighted sum of separate similarities.
    hybrid = hstack(
        [CONTENT_WEIGHT * content, COLLABORATIVE_WEIGHT * csr_matrix(collaborative)],
        format="csr",
    )
    if not np.isfinite(hybrid.data).all():
        raise ValueError("Hybrid features contain NaN or infinity; cache not modified.")
    zero_rows = int(np.count_nonzero(np.asarray(hybrid.getnnz(axis=1)) == 0))
    if zero_rows:
        LOGGER.warning(
            "%d products have neither usable text nor history; their matches have "
            "zero similarity and are uninformative ties.", zero_rows,
        )

    # Exact cosine KNN accepts sparse vectors. Query in batches instead of storing
    # an entire N x N similarity matrix (exact search still has quadratic cost).
    result_count = min(TOP_K, product_count - 1)
    neighbors = NearestNeighbors(metric="cosine", algorithm="brute", n_jobs=-1)
    neighbors.fit(hybrid)
    recommendations: list[Recommendation] = []
    for start in range(0, product_count, QUERY_BATCH_SIZE):
        distances, indices = neighbors.kneighbors(
            hybrid[start:start + QUERY_BATCH_SIZE], n_neighbors=result_count + 1
        )
        for offset, (row_distances, row_indices) in enumerate(zip(distances, indices)):
            target_index = start + offset
            # Remove self by identity, not position: duplicate/zero vectors can
            # cause self to appear anywhere among equal-distance neighbors.
            candidates = [
                (float(distance), product_ids[int(index)])
                for distance, index in zip(row_distances, row_indices)
                if int(index) != target_index
            ]
            candidates.sort(key=lambda candidate: (candidate[0], candidate[1]))
            for rank, (distance, candidate_id) in enumerate(candidates[:result_count], 1):
                # Cosine similarity can legitimately be negative for SVD factors.
                # Clip only floating-point drift outside its mathematical range.
                score = float(np.clip(1.0 - distance, -1.0, 1.0))
                if not np.isfinite(score):
                    raise ValueError("Non-finite recommendation score; cache not modified.")
                recommendations.append((product_ids[target_index], candidate_id, score, rank))
    LOGGER.info("Built %d recommendations (up to %d per product).", len(recommendations), TOP_K)
    return recommendations


def persist_recommendations(
    config: dict[str, Any], recommendations: list[Recommendation]
) -> None:
    """Atomically replace the existing InnoDB cache with a computed replacement."""
    with closing(mysql.connector.connect(**config)) as connection:
        with closing(connection.cursor()) as cursor:
            # This connection-scoped advisory lock prevents simultaneous refreshes
            # from interleaving their writes. Closing the connection releases it,
            # including on exceptions; GET_LOCK is independent of transactions.
            cursor.execute("SELECT GET_LOCK(%s, 0)", ("clothing_shop.recommendation_refresh",))
            lock_result = cursor.fetchone()
            if not lock_result or lock_result[0] != 1:
                raise RuntimeError("Another recommendation cache refresh is in progress.")

            # DELETE and INSERT are atomic only on a transactional storage engine.
            # Check the live table rather than assuming its schema matches a dump.
            cursor.execute(
                """
                SELECT ENGINE
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = %s
                """,
                ("product_recommendations",),
            )
            engine_row = cursor.fetchone()
            if not engine_row:
                raise RuntimeError("Table product_recommendations does not exist.")
            if str(engine_row[0]).upper() != "INNODB":
                raise RuntimeError(
                    "Atomic cache refresh requires product_recommendations to use InnoDB."
                )

            # Clear any implicit read transaction before opening the write transaction.
            # The advisory lock remains held across rollback until this connection closes.
            connection.rollback()
            try:
                connection.start_transaction()
                LOGGER.info("Replacing recommendation cache in one transaction.")
                cursor.execute("DELETE FROM product_recommendations")
                for start in range(0, len(recommendations), INSERT_BATCH_SIZE):
                    cursor.executemany(
                        INSERT_RECOMMENDATIONS_SQL,
                        recommendations[start:start + INSERT_BATCH_SIZE],
                    )
                # Commit once after ALL batches, including the empty-catalog case.
                connection.commit()
            except BaseException:
                # Also attempt rollback on Ctrl+C, but preserve the original error.
                try:
                    connection.rollback()
                except mysql.connector.Error:
                    LOGGER.exception("Rollback failed; database cache state is uncertain.")
                else:
                    LOGGER.error(
                        "Atomic refresh failed; the previous cache was restored."
                    )
                raise
    LOGGER.info("Persisted %d recommendations.", len(recommendations))


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--dry-run", action="store_true", help="Build recommendations without updating MySQL."
    )
    args = parser.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
    try:
        config = database_config()
        products, purchases = extract_data(config)
        recommendations = build_recommendations(products, purchases)
        if args.dry_run:
            LOGGER.info(
                "Dry run complete: %d recommendations; no database writes.", len(recommendations)
            )
        else:
            persist_recommendations(config, recommendations)
        return 0
    except KeyboardInterrupt:
        LOGGER.warning("Recommendation refresh interrupted.")
        return 130
    except Exception:
        LOGGER.exception("Recommendation refresh failed.")
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
