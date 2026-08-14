package vn.covie.catalog.api;

import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.covie.catalog.application.CategoryQueryService;
import vn.covie.catalog.model.CatalogItemType;

@RestController
@RequestMapping("/api/v1/categories")
public class CategoryController {
    private final CategoryQueryService categories;

    public CategoryController(CategoryQueryService categories) {
        this.categories = categories;
    }

    @GetMapping
    public List<CategoryQueryService.CategorySummary> list(@RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) CatalogItemType type) {
        return categories.list(parentId, type);
    }

    @GetMapping("/{categoryId}")
    public CategoryQueryService.CategoryDetail detail(@PathVariable UUID categoryId) {
        return categories.detail(categoryId);
    }
}
