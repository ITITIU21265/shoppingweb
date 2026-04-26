package com.web.shoppingweb.dto.saved;

public class SavedToggleResponseDTO {

    private boolean saved;
    private long savedCount;

    public SavedToggleResponseDTO() {
    }

    public SavedToggleResponseDTO(boolean saved, long savedCount) {
        this.saved = saved;
        this.savedCount = savedCount;
    }

    public boolean isSaved() {
        return saved;
    }

    public void setSaved(boolean saved) {
        this.saved = saved;
    }

    public long getSavedCount() {
        return savedCount;
    }

    public void setSavedCount(long savedCount) {
        this.savedCount = savedCount;
    }
}
