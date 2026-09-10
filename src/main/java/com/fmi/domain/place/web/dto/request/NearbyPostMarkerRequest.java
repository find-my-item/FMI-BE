package com.fmi.domain.place.web.dto.request;

import com.fmi.domain.Enum.Category;
import com.fmi.domain.post.data.PostStatus;
import com.fmi.domain.post.data.PostType;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NearbyPostMarkerRequest {

    private PostType postType;
    private PostStatus postStatus;
    private Category category;
}
