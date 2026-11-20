package com.liskovsoft.mediaserviceinterfaces;

import com.liskovsoft.mediaserviceinterfaces.data.CommentGroup;
import io.reactivex.Observable;

public interface CommentsService {
    Observable<CommentGroup> getCommentsObserve(String key);
    Observable<Void> toggleLikeObserve(String key);
    /** Toggles a comment using its own like/unlike action key. */
    Observable<Void> toggleLikeActionObserve(String actionKey);
    Observable<Void> toggleDislikeObserve(String key);
}
