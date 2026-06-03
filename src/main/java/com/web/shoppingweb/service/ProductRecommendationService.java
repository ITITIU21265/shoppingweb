package com.web.shoppingweb.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.web.shoppingweb.entity.order.OrderItem;
import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.repository.order.OrderItemRepository;
import com.web.shoppingweb.repository.product.ProductRepository;

@Service
public class ProductRecommendationService {

    private static final double ALPHA = 0.3;

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    private volatile Map<Long, Map<String, Double>> productVectors = Collections.emptyMap();
    private volatile Map<String, Integer> documentFrequency = Collections.emptyMap();
    private volatile Map<Long, Map<Long, Double>> collaborativeMatrix = Collections.emptyMap();
    private volatile int totalDocuments = 0;

    public ProductRecommendationService(ProductRepository productRepository,
                                        OrderItemRepository orderItemRepository) {
        this.productRepository = productRepository;
        this.orderItemRepository = orderItemRepository;
    }

    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional(readOnly = true)
    public void trainModel() {
        rebuildModel();
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional(readOnly = true)
    public void trainModelOnStartup() {
        rebuildModel();
    }

    private void rebuildModel() {
        List<Product> allProducts = productRepository.findAll();
        totalDocuments = allProducts.size();

        if (totalDocuments == 0) {
            productVectors = Collections.emptyMap();
            documentFrequency = Collections.emptyMap();
            collaborativeMatrix = Collections.emptyMap();
            return;
        }

        Map<Long, List<String>> productTokens = new HashMap<>();
        Map<String, Integer> trainedDocumentFrequency = new HashMap<>();

        for (Product product : allProducts) {
            List<String> tokens = tokenize(product.getName() + " " + product.getDescription());
            productTokens.put(product.getId(), tokens);

            Set<String> uniqueTokens = new HashSet<>(tokens);
            for (String token : uniqueTokens) {
                trainedDocumentFrequency.merge(token, 1, ProductRecommendationService::mergeCounts);
            }
        }

        documentFrequency = Collections.unmodifiableMap(trainedDocumentFrequency);

        Map<Long, Map<String, Double>> trainedProductVectors = new HashMap<>();
        for (Product product : allProducts) {
            List<String> tokens = productTokens.get(product.getId());
            Map<String, Double> tfidfVector = calculateTFIDF(tokens);
            trainedProductVectors.put(product.getId(), tfidfVector);
        }

        productVectors = freezeStringMatrix(trainedProductVectors);
        collaborativeMatrix = buildCollaborativeMatrix();
    }

    private Map<Long, Map<Long, Double>> buildCollaborativeMatrix() {
        List<OrderItem> orderItems = orderItemRepository.findAll();

        // Treat each order as a basket and each product as a binary item in that basket.
        Map<Long, Set<Long>> productsByOrder = orderItems.stream()
                .filter(orderItem -> orderItem.getOrder() != null)
                .filter(orderItem -> orderItem.getOrder().getId() != null)
                .filter(orderItem -> orderItem.getProduct() != null)
                .filter(orderItem -> orderItem.getProduct().getId() != null)
                .collect(Collectors.groupingBy(
                        orderItem -> orderItem.getOrder().getId(),
                        Collectors.mapping(orderItem -> orderItem.getProduct().getId(), Collectors.toSet())
                ));

        Map<Long, Integer> orderFrequencyByProduct = new HashMap<>();
        Map<Long, Map<Long, Integer>> coOccurrenceCounts = new HashMap<>();

        for (Set<Long> productIdsInOrder : productsByOrder.values()) {
            if (productIdsInOrder.isEmpty()) {
                continue;
            }

            for (Long productId : productIdsInOrder) {
                orderFrequencyByProduct.merge(productId, 1, ProductRecommendationService::mergeCounts);
            }

            List<Long> productIds = new ArrayList<>(productIdsInOrder);
            for (int i = 0; i < productIds.size(); i++) {
                for (int j = i + 1; j < productIds.size(); j++) {
                    Long firstProductId = productIds.get(i);
                    Long secondProductId = productIds.get(j);

                    coOccurrenceCounts
                            .computeIfAbsent(firstProductId, key -> new HashMap<>())
                            .merge(secondProductId, 1, ProductRecommendationService::mergeCounts);
                    coOccurrenceCounts
                            .computeIfAbsent(secondProductId, key -> new HashMap<>())
                            .merge(firstProductId, 1, ProductRecommendationService::mergeCounts);
                }
            }
        }

        Map<Long, Map<Long, Double>> trainedCollaborativeMatrix = new HashMap<>();

        for (Map.Entry<Long, Map<Long, Integer>> productEntry : coOccurrenceCounts.entrySet()) {
            Long productId = productEntry.getKey();
            int productOrderFrequency = orderFrequencyByProduct.getOrDefault(productId, 0);

            if (productOrderFrequency == 0) {
                continue;
            }

            for (Map.Entry<Long, Integer> relatedEntry : productEntry.getValue().entrySet()) {
                Long relatedProductId = relatedEntry.getKey();
                int relatedOrderFrequency = orderFrequencyByProduct.getOrDefault(relatedProductId, 0);

                if (relatedOrderFrequency == 0) {
                    continue;
                }

                // Item-based collaborative cosine similarity over product-order co-occurrence.
                double collaborativeScore = relatedEntry.getValue()
                        / Math.sqrt((double) productOrderFrequency * relatedOrderFrequency);

                trainedCollaborativeMatrix
                        .computeIfAbsent(productId, key -> new HashMap<>())
                        .put(relatedProductId, collaborativeScore);
            }
        }

        return freezeLongMatrix(trainedCollaborativeMatrix);
    }

    private List<String> tokenize(String text) {
        if (text == null) {
            return Collections.emptyList();
        }

        return Arrays.stream(text.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .split("\\s+"))
                .filter(word -> word.length() > 2)
                .collect(Collectors.toList());
    }

    private Map<String, Double> calculateTFIDF(List<String> tokens) {
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : tokens) {
            termFrequency.merge(token, 1, ProductRecommendationService::mergeCounts);
        }

        Map<String, Double> tfidfVector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            int df = documentFrequency.getOrDefault(term, 1);

            double tfidf = tf * Math.log((double) totalDocuments / df);
            tfidfVector.put(term, tfidf);
        }

        return tfidfVector;
    }

    private static Integer mergeCounts(Integer currentValue, Integer incrementValue) {
        int current = currentValue == null ? 0 : currentValue.intValue();
        int increment = incrementValue == null ? 0 : incrementValue.intValue();
        return current + increment;
    }

    private double calculateCosineSimilarity(Map<String, Double> vectorA, Map<String, Double> vectorB) {
        if (vectorA.isEmpty() || vectorB.isEmpty()) {
            return 0.0;
        }

        double dotProduct = 0.0;
        double magnitudeA = 0.0;
        double magnitudeB = 0.0;

        Set<String> allTerms = new HashSet<>();
        allTerms.addAll(vectorA.keySet());
        allTerms.addAll(vectorB.keySet());

        for (String term : allTerms) {
            double valueA = vectorA.getOrDefault(term, 0.0);
            double valueB = vectorB.getOrDefault(term, 0.0);

            dotProduct += valueA * valueB;
            magnitudeA += valueA * valueA;
            magnitudeB += valueB * valueB;
        }

        magnitudeA = Math.sqrt(magnitudeA);
        magnitudeB = Math.sqrt(magnitudeB);

        if (magnitudeA == 0.0 || magnitudeB == 0.0) {
            return 0.0;
        }

        return dotProduct / (magnitudeA * magnitudeB);
    }

    public List<Product> getRelatedProducts(Long targetProductId, int limit) {
        if (targetProductId == null || limit <= 0) {
            return Collections.emptyList();
        }

        Map<Long, Map<String, Double>> currentProductVectors = productVectors;
        Map<String, Double> targetVector = currentProductVectors.get(targetProductId);
        if (targetVector == null) {
            return Collections.emptyList();
        }

        Map<Long, Double> targetCollaborativeScores = collaborativeMatrix.getOrDefault(targetProductId, Collections.emptyMap());
        List<ProductSimilarity> similarities = new ArrayList<>();

        for (Map.Entry<Long, Map<String, Double>> entry : currentProductVectors.entrySet()) {
            Long productId = entry.getKey();
            if (productId.equals(targetProductId)) {
                continue;
            }

            Map<String, Double> candidateVector = entry.getValue();
            double contentScore = calculateCosineSimilarity(targetVector, candidateVector);
            double collabScore = targetCollaborativeScores.getOrDefault(productId, 0.0);
            // Hybrid ranking favors order behavior while preserving TF-IDF for cold-start items.
            double hybridScore = (ALPHA * contentScore) + ((1 - ALPHA) * collabScore);

            similarities.add(new ProductSimilarity(productId, hybridScore));
        }

        List<Long> relatedProductIds = similarities.stream()
                .sorted(Comparator.comparingDouble(ProductSimilarity::getSimilarity).reversed())
                .limit(limit)
                .map(ProductSimilarity::getProductId)
                .collect(Collectors.toList());

        Map<Long, Product> productsById = productRepository.findAllById(relatedProductIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity(), (existing, replacement) -> existing));

        return relatedProductIds.stream()
                .map(productsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private Map<Long, Map<String, Double>> freezeStringMatrix(Map<Long, Map<String, Double>> matrix) {
        Map<Long, Map<String, Double>> frozenMatrix = new HashMap<>();
        for (Map.Entry<Long, Map<String, Double>> entry : matrix.entrySet()) {
            frozenMatrix.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozenMatrix);
    }

    private Map<Long, Map<Long, Double>> freezeLongMatrix(Map<Long, Map<Long, Double>> matrix) {
        Map<Long, Map<Long, Double>> frozenMatrix = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : matrix.entrySet()) {
            frozenMatrix.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozenMatrix);
    }

    private static class ProductSimilarity {
        private final Long productId;
        private final double similarity;

        public ProductSimilarity(Long productId, double similarity) {
            this.productId = productId;
            this.similarity = similarity;
        }

        public Long getProductId() {
            return productId;
        }

        public double getSimilarity() {
            return similarity;
        }
    }
}
