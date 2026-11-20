package com.liskovsoft.mediaserviceinterfaces.data;

import androidx.annotation.Nullable;

public interface CommentItem {
    @Nullable
    String getId();
    @Nullable
    String getMessage();
    @Nullable
    String getAuthorName();
    @Nullable
    String getAuthorPhoto();
    @Nullable
    String getPublishedDate();
    @Nullable
    String getNestedCommentsKey();
    /** The action key for this comment's like/unlike button, or null if it has one. */
    @Nullable
    String getLikeActionKey();
    /** The action key for this comment's dislike button, or null if it has one. */
    @Nullable
    String getDislikeActionKey();
    boolean isLiked();
    @Nullable
    String getLikeCount();
    @Nullable
    String getReplyCount();
    boolean isEmpty();
}

