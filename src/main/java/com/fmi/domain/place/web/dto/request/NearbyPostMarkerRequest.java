package com.fmi.domain.place.web.dto.request;

import com.fmi.domain.Enum.Category;
import com.fmi.domain.post.data.PostStatus;
import com.fmi.domain.post.data.PostType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NearbyPostMarkerRequest {

    @Min(1) @Max(8) private int level;

    private PostType postType;
    private PostStatus postStatus;
    private Category category;
}
