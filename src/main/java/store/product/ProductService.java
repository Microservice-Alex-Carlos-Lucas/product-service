package store.product;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository repository;

    public ProductResponse create(ProductRequest request) {
        Product product = new Product(null, request.name(), request.price(), request.unit());
        return ProductResponse.from(repository.save(product));
    }

    public List<ProductResponse> list(String name) {
        List<Product> products = (name == null || name.isBlank())
                ? repository.findAll()
                : repository.findByNameContainingIgnoreCase(name);
        return products.stream().map(ProductResponse::from).toList();
    }

    public ProductResponse get(UUID id) {
        return repository.findById(id)
                .map(ProductResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found"));
    }

    public void delete(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        repository.deleteById(id);
    }
}
