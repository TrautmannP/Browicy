package com.browicy.engine;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class PageLoadProgressTest {

    @Test
    public void describeShowsPhaseActivityAndScriptCounts() {
        PageLoadProgress progress = new PageLoadProgress();
        assertEquals(PageLoadProgress.Phase.QUEUED, progress.snapshot().phase());

        progress.phase(PageLoadProgress.Phase.RUNNING_SCRIPTS, "app.js");
        progress.scriptPlanned();
        progress.scriptPlanned();
        progress.scriptExecuted();

        PageLoadProgress.Snapshot snapshot = progress.snapshot();
        String description = snapshot.describe();
        assertTrue(description, description.contains("Führe Skripte aus"));
        assertTrue(description, description.contains("app.js"));
        assertTrue(description, description.contains("(1/2)"));
    }

    @Test
    public void pendingResourceCountsAppearWhileLoadingResources() {
        PageLoadProgress progress = new PageLoadProgress();
        progress.phase(PageLoadProgress.Phase.LOADING_RESOURCES, "");
        progress.imageStarted();
        progress.imageStarted();
        progress.imageFinished();
        progress.fontStarted();

        PageLoadProgress.Snapshot snapshot = progress.snapshot();
        assertEquals(2, snapshot.totalImages());
        assertEquals(1, snapshot.pendingImages());
        String description = snapshot.describe();
        assertTrue(description, description.contains("1 Bilder ausstehend"));
        assertTrue(description, description.contains("1 Schriften ausstehend"));

        progress.imageFinished();
        progress.fontFinished();
        progress.phase(PageLoadProgress.Phase.COMPLETE, "");
        assertEquals(PageLoadProgress.Phase.COMPLETE, progress.snapshot().phase());
        assertEquals(0, progress.snapshot().pendingImages());
    }
}
