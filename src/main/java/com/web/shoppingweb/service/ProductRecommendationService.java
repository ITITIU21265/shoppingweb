package com.web.shoppingweb.service;

import java.util.*;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.web.shoppingweb.entity.product.Product;
import com.web.shoppingweb.repository.product.ProductRepository;

import jakarta.annotation.PostConstruct;

@Service
public class ProductRecommendationService {

    private final ProductRepository productRepository;
    private final Map<Long, Map<String, Double>> productVectors = new HashMap<>();
    private final Map<String, Integer> documentFrequency = new HashMap<>();
    private int totalDocuments = 0;

    public ProductRecommendationService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @PostConstruct
    public void trainModel() {
        List<Product> allProducts = productRepository.findAll();
        totalDocuments = allProducts.size();

        if (totalDocuments == 0) {
            return;
        }

        Map<Long, List<String>> productTokens = new HashMap<>();
        for (Product product : allProducts) {
            List<String> tokens = tokenize(product.getName() + " " + product.getDescription());
            productTokens.put(product.getId(), tokens);

            Set<String> uniqueTokens = new HashSet<>(tokens);
            for (String token : uniqueTokens) {
                documentFrequency.merge(token, 1, Integer::sum);
            }
        }

        for (Product product : allProducts) {
            List<String> tokens = productTokens.get(product.getId());
            Map<String, Double> tfidfVector = calculateTFIDF(tokens);
            productVectors.put(product.getId(), tfidfVector);
        }
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
            termFrequency.merge(token, 1, Integer::sum);
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
        Map<String, Double> targetVector = productVectors.get(targetProductId);
        if (targetVector == null) {
            return Collections.emptyList();
        }

        List<ProductSimilarity> similarities = new ArrayList<>();

        for (Map.Entry<Long, Map<String, Double>> entry : productVectors.entrySet()) {
            Long productId = entry.getKey();
            if (productId.equals(targetProductId)) {
                continue;
            }

            Map<String, Double> candidateVector = entry.getValue();
            double similarity = calculateCosineSimilarity(targetVector, candidateVector);
            similarities.add(new ProductSimilarity(productId, similarity));
        }

        return similarities.stream()
                .sorted(Comparator.comparingDouble(ProductSimilarity::getSimilarity).reversed())
                .limit(limit)
                .map(ps -> productRepository.findById(ps.getProductId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
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
