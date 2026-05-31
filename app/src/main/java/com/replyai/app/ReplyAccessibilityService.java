package com.replyai.app;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.ArrayList;
import java.util.List;

/**
 * Accessibility Service that runs in the background.
 * When Twitter/X is open, it scans the visible screen for tweet text
 * and sends it to the FloatingService so the bubble can auto-fill it.
 *
 * HOW IT WORKS:
 * 1. Detects when Twitter/X window changes (user scrolls to a tweet)
 * 2. Walks the view hierarchy to find the longest text block (the tweet)
 * 3. Broadcasts the tweet text to FloatingService via a local broadcast
 * 4. Floating panel auto-fills the tweet text — user just picks tone & taps Generate
 */
public class ReplyAccessibilityService extends AccessibilityService {

    // Broadcast action so FloatingService can receive extracted tweet
    public static final String ACTION_TWEET_DETECTED = "com.replyai.TWEET_DETECTED";
    public static final String EXTRA_TWEET_TEXT = "tweet_text";

    // Minimum characters to consider a block as a "tweet"
    private static final int MIN_TWEET_LENGTH = 15;
    // Maximum characters (tweets are 280 max)
    private static final int MAX_TWEET_LENGTH = 600;

    private String lastSentTweet = "";

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) return;

        int type = event.getEventType();
        // Only act on content changes and window state changes
        if (type != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED &&
            type != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            type != AccessibilityEvent.TYPE_VIEW_SCROLLED) return;

        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;

        String tweet = extractMainTweetText(root);
        root.recycle();

        if (tweet != null && !tweet.equals(lastSentTweet)) {
            lastSentTweet = tweet;
            broadcastTweet(tweet);
        }
    }

    /**
     * Walks the accessibility tree and finds the most likely tweet text.
     * Strategy: collect all visible text nodes, filter by length, pick best candidate.
     */
    private String extractMainTweetText(AccessibilityNodeInfo root) {
        List<String> candidates = new ArrayList<>();
        collectTextNodes(root, candidates, 0);

        if (candidates.isEmpty()) return null;

        // Pick the longest text that fits tweet length range
        String best = null;
        int bestLen = 0;
        for (String text : candidates) {
            int len = text.length();
            if (len >= MIN_TWEET_LENGTH && len <= MAX_TWEET_LENGTH) {
                if (len > bestLen) {
                    bestLen = len;
                    best = text;
                }
            }
        }
        return best;
    }

    private void collectTextNodes(AccessibilityNodeInfo node, List<String> results, int depth) {
        if (node == null || depth > 12) return;

        CharSequence text = node.getText();
        if (text != null) {
            String str = text.toString().trim();
            // Filter out UI labels, short strings, URLs-only, usernames
            if (str.length() >= MIN_TWEET_LENGTH
                    && !str.startsWith("@")
                    && !str.startsWith("http")
                    && !str.matches("^[\\d,.KMk]+$")  // numbers only
                    && str.contains(" ")               // must have spaces (real sentence)
                    && !str.equals("Home")
                    && !str.equals("Search")
                    && !str.equals("Notifications")
                    && !str.equals("Messages")) {
                results.add(str);
            }
        }

        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child != null) {
                collectTextNodes(child, results, depth + 1);
                child.recycle();
            }
        }
    }

    private void broadcastTweet(String tweet) {
        Intent intent = new Intent(ACTION_TWEET_DETECTED);
        intent.putExtra(EXTRA_TWEET_TEXT, tweet);
        sendBroadcast(intent);
    }

    @Override
    public void onInterrupt() {}
}
