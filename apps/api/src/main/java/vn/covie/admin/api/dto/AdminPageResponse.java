package vn.covie.admin.api.dto;

import java.util.List;

public record AdminPageResponse<T>(List<T> items,long totalElements,int page,int size,int totalPages){
    public AdminPageResponse { items=List.copyOf(items); }
}
