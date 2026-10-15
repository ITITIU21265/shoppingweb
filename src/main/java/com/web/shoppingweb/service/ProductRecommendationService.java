package com.web.shoppingweb.service;

import java.sql.SQLException;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(ProductRecommendationService.class);
    private static final double ALPHA = 0.3;

    private final ProductRepository productRepository;
    private final OrderItemRepository orderItemRepository;

    // MA TRẬN DUY NHẤT LƯU TRỮ KẾT QUẢ ĐÃ TÍNH SẴN CỦA TOÀN BỘ HỆ THỐNG
    private volatile Map<Long, Map<Long, Double>> precomputedHybridMatrix = Collections.emptyMap();
    
    private volatile Map<String, Integer> documentFrequency = Collections.emptyMap();
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
            precomputedHybridMatrix = Collections.emptyMap();
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

        Map<Long, Map<String, Double>> tfidfVectors = new HashMap<>();
        for (Product product : allProducts) {
            List<String> tokens = productTokens.get(product.getId());
            tfidfVectors.put(product.getId(), calculateTFIDF(tokens));
        }

        Map<Long, Map<Long, Double>> collabMatrix = buildCollaborativeMatrix();

        Map<Long, Map<Long, Double>> trainedHybridMatrix = new HashMap<>();
        
        List<Long> productIds = allProducts.stream().map(Product::getId).toList();
        
        for (Long targetId : productIds) {
            Map<Long, Double> scoresForTarget = new HashMap<>();
            Map<String, Double> targetVector = tfidfVectors.get(targetId);
            Map<Long, Double> targetCollabScores = collabMatrix.getOrDefault(targetId, Collections.emptyMap());

            for (Long candidateId : productIds) {
                if (targetId.equals(candidateId)) continue;
                double contentScore = calculateCosineSimilarity(targetVector, tfidfVectors.get(candidateId));
                double collabScore = targetCollabScores.getOrDefault(candidateId, 0.0);
                double hybridScore = (ALPHA * contentScore) + ((1 - ALPHA) * collabScore);
                
                if (hybridScore > 0) {
                    scoresForTarget.put(candidateId, hybridScore);
                }
            }
            trainedHybridMatrix.put(targetId, scoresForTarget);
        }

        precomputedHybridMatrix = freezeLongMatrix(trainedHybridMatrix);
    }

    private Map<Long, Map<Long, Double>> buildCollaborativeMatrix() {
        List<OrderItem> orderItems = orderItemRepository.findAll();

        Map<Long, Set<Long>> productsByOrder = orderItems.stream()
                .filter(orderItem -> orderItem.getOrder() != null && orderItem.getProduct() != null)
                .collect(Collectors.groupingBy(
                        orderItem -> orderItem.getOrder().getId(),
                        Collectors.mapping(orderItem -> orderItem.getProduct().getId(), Collectors.toSet())
                ));

        Map<Long, Integer> orderFrequencyByProduct = new HashMap<>();
        Map<Long, Map<Long, Integer>> coOccurrenceCounts = new HashMap<>();

        for (Set<Long> productIdsInOrder : productsByOrder.values()) {
            if (productIdsInOrder.isEmpty()) continue;

            for (Long productId : productIdsInOrder) {
                orderFrequencyByProduct.merge(productId, 1, ProductRecommendationService::mergeCounts);
            }

            List<Long> productIds = new ArrayList<>(productIdsInOrder);
            for (int i = 0; i < productIds.size(); i++) {
                for (int j = i + 1; j < productIds.size(); j++) {
                    Long id1 = productIds.get(i);
                    Long id2 = productIds.get(j);

                    coOccurrenceCounts.computeIfAbsent(id1, k -> new HashMap<>()).merge(id2, 1, ProductRecommendationService::mergeCounts);
                    coOccurrenceCounts.computeIfAbsent(id2, k -> new HashMap<>()).merge(id1, 1, ProductRecommendationService::mergeCounts);
                }
            }
        }

        Map<Long, Map<Long, Double>> trainedCollaborativeMatrix = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Integer>> productEntry : coOccurrenceCounts.entrySet()) {
            Long productId = productEntry.getKey();
            int productOrderFrequency = orderFrequencyByProduct.getOrDefault(productId, 0);

            if (productOrderFrequency == 0) continue;

            for (Map.Entry<Long, Integer> relatedEntry : productEntry.getValue().entrySet()) {
                Long relatedProductId = relatedEntry.getKey();
                int relatedOrderFrequency = orderFrequencyByProduct.getOrDefault(relatedProductId, 0);

                if (relatedOrderFrequency == 0) continue;

                double collaborativeScore = relatedEntry.getValue() / Math.sqrt((double) productOrderFrequency * relatedOrderFrequency);
                trainedCollaborativeMatrix.computeIfAbsent(productId, k -> new HashMap<>()).put(relatedProductId, collaborativeScore);
            }
        }
        return trainedCollaborativeMatrix;
    }

    private List<String> tokenize(String text) {
        if (text == null) return Collections.emptyList();
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-z0-9\\s]", " ").split("\\s+"))
                .filter(word -> word.length() > 2)
                .collect(Collectors.toList());
    }

    private Map<String, Double> calculateTFIDF(List<String> tokens) {
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String token : tokens) termFrequency.merge(token, 1, ProductRecommendationService::mergeCounts);

        Map<String, Double> tfidfVector = new HashMap<>();
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            String term = entry.getKey();
            int tf = entry.getValue();
            int df = documentFrequency.getOrDefault(term, 1);
            tfidfVector.put(term, tf * Math.log((double) totalDocuments / df));
        }
        return tfidfVector;
    }

    private double calculateCosineSimilarity(Map<String, Double> vectorA, Map<String, Double> vectorB) {
        if (vectorA.isEmpty() || vectorB.isEmpty()) return 0.0;

        double dotProduct = 0.0, magnitudeA = 0.0, magnitudeB = 0.0;
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
        if (magnitudeA == 0.0 || magnitudeB == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(magnitudeA) * Math.sqrt(magnitudeB));
    }

    public List<Product> getRelatedProducts(Long targetProductId, int limit) {
        if (targetProductId == null || limit <= 0) return Collections.emptyList();

        try {
            List<Product> cachedProducts = productRepository.findRecommendedProducts(targetProductId, limit);
            if (!cachedProducts.isEmpty()) {
                return cachedProducts;
            }
        } catch (RuntimeException exception) {
            if (!isMissingRecommendationCacheTable(exception)) {
                throw exception;
            }
            LOGGER.warn("Recommendation cache table is unavailable; using the in-memory fallback.");
        }

        return getRelatedProductsFromInMemoryEngine(targetProductId, limit);
    }

    private List<Product> getRelatedProductsFromInMemoryEngine(Long targetProductId, int limit) {
        Map<Long, Double> precomputedScores = precomputedHybridMatrix.get(targetProductId);
        if (precomputedScores == null || precomputedScores.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> relatedProductIds = precomputedScores.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .limit(limit)
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, Product> productsById = productRepository.findAllById(relatedProductIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        return relatedProductIds.stream()
                .map(productsById::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    private boolean isMissingRecommendationCacheTable(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof SQLException sqlException
                    && sqlException.getErrorCode() == 1146
                    && "42S02".equals(sqlException.getSQLState())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private static Integer mergeCounts(Integer currentValue, Integer incrementValue) {
        return (currentValue == null ? 0 : currentValue) + (incrementValue == null ? 0 : incrementValue);
    }

    private Map<Long, Map<Long, Double>> freezeLongMatrix(Map<Long, Map<Long, Double>> matrix) {
        Map<Long, Map<Long, Double>> frozenMatrix = new HashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : matrix.entrySet()) {
            frozenMatrix.put(entry.getKey(), Collections.unmodifiableMap(entry.getValue()));
        }
        return Collections.unmodifiableMap(frozenMatrix);
    }
}
