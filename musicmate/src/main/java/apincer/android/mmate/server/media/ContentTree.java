package apincer.android.mmate.server.media;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import apincer.android.mmate.Constants;

/**
 * Based on a class from WireMe and used under Apache 2 License. See
 * https://code.google.com/p/wireme/ for more details.
 */
public class ContentTree {

    private static final Logger LOG = LoggerFactory.getLogger(ContentTree.class);

    private final Map<String, ContentNode> contentNodes = new ConcurrentHashMap<>();
    private final ContentNode rootNode;
    private final Map<String, ContentItem> contentItems = new ConcurrentHashMap<>();

    private static final int MAX_RECENT_ITEMS = 200;
    private final ContentNode recentNode;
    private final NavigableSet<ContentItem> recent = new ConcurrentSkipListSet<>(ContentItem.Order.MODIFIED_DESC);
    private final Object[] recentLock = new Object[] {};
    private volatile long oldestRecentItem = 0L;

    public ContentTree () {
        this(true);
    }

    public ContentTree (final boolean trackRecent) {
        this.rootNode = new ContentNode(ContentGroup.ROOT.getId(), "-1", ContentGroup.ROOT.getHumanName(), Constants.METADATA_MODEL_NAME);
        addNode(this.rootNode);

        if (trackRecent) {
            this.recentNode = new ContentNode(ContentGroup.RECENT.getId(), this.rootNode.getId(), ContentGroup.RECENT.getHumanName(), null, null, this.recent);
            // TODO mark recent as not searchable.
            addNode(this.recentNode);
            this.rootNode.addNodeIfAbsent(this.recentNode);
        }
        else {
            this.recentNode = null;
        }
    }

    public int getNodeCount() {
        return this.contentNodes.size();
    }

    public int getItemCount() {
        return this.contentItems.size();
    }

    public ContentNode getRootNode () {
        return this.rootNode;
    }

    Collection<ContentNode> getNodes () {
        return this.contentNodes.values();
    }

    public Collection<ContentItem> getItems() {
        return this.contentItems.values();
    }

    public ContentNode getNode (final String id) {
        if (id == null) throw new NullPointerException("Cannot get node with null id.");
        return this.contentNodes.get(id);
    }

    public void addNode (final ContentNode node) {
        this.contentNodes.put(node.getId(), node);
    }

    public ContentItem getItem(final String id) {
        if (id == null) throw new NullPointerException("Cannot get item with null id.");
        return this.contentItems.get(id);
    }

    public void addItem(final ContentItem item) {
        this.contentItems.put(item.getId(), item);
        maybeAddToRecent(item);
    }

    public List<ContentItem> getItemsForIds(final List<String> ids, final String username) {
        final List<ContentItem> ret = new ArrayList<>();
        for (final String id : ids) {
            final ContentItem item = getItem(id);
            if (item == null) {
                LOG.debug("ID not found in content tree: {}", id);
                continue;
            }
            final ContentNode node = getNode(item.getParentId());
           // if (!node.isUserAuth(username)) continue;
            ret.add(item);
        }
        return ret;
    }

    /**
     * Returns number of items removed.
     */
    public int removeFile (final File file) {
        if (file == null) throw new IllegalArgumentException("file can not be null.");
        int removeCount = 0;

        // FIXME this is very lazy and not efficient.

        final Iterator<Entry<String, ContentNode>> nodeIttr = this.contentNodes.entrySet().iterator();
        while (nodeIttr.hasNext()) {
            final Entry<String, ContentNode> e = nodeIttr.next();
            if (file.equals(e.getValue().getFile())) {
                nodeIttr.remove();
                removeNodesAndItemsInNode(e.getValue());
                removeNodeFromParent(e.getValue());
                removeCount += 1;
            }
        }

        final Iterator<Entry<String, ContentItem>> itemIttr = this.contentItems.entrySet().iterator();
        while (itemIttr.hasNext()) {
            final Entry<String, ContentItem> e = itemIttr.next();
            if (file.equals(e.getValue().getFile())) {
                itemIttr.remove();
                removeItemFromParent(e.getValue());
                removeFromRecent(e.getValue());
                removeCount += 1;
            }
        }

        return removeCount;
    }

    private void removeNodesAndItemsInNode(final ContentNode node) {
        node.withEachNode((n) -> {
            this.contentNodes.remove(n.getId());

            // in theory this recursively getting a lock on a node via withEachNode() while within a lock
            // could deadlock, but lets see if that ever happens in practice before switching to a list copy.
            removeNodesAndItemsInNode(n);
        });
        node.withEachItem((i) -> {
            this.contentItems.remove(i.getId());
        });
    }

    private void removeNodeFromParent(final ContentNode node) {
        final ContentNode parentNode = this.contentNodes.get(node.getParentId());
        if (parentNode == null) {
            LOG.error("Parent of container '{}' not found in contentMap: '{}'.", node.getId(), node.getParentId());
            return;
        }
        if (!parentNode.removeNode(node)) {
            LOG.error("Container '{}' not in its parent: '{}'.", node.getId(), node.getParentId());
        }
        if (isContainerEmptyAndRemoveable(parentNode)) {
            this.contentNodes.remove(parentNode.getId());
            removeNodeFromParent(parentNode);
        }
    }

    private static boolean isContainerEmptyAndRemoveable(final ContentNode node) {
        return node.getNodeAndItemCount() < 1 && !ContentGroup.incluesId(node.getId());
    }

    private void removeItemFromParent(final ContentItem item) {
        if (item.getParentId() == null) return;

        final ContentNode parentNode = this.contentNodes.get(item.getParentId());
        if (parentNode == null) {
            LOG.error("Parent of item '{}' not found in contentMap: '{}'.", item.getId(), item.getParentId());
            return;
        }
        if (!parentNode.removeItem(item)) {
            LOG.error("Item '{}' not in its parent: '{}'.", item.getId(), item.getParentId());
        }
        if (isContainerEmptyAndRemoveable(parentNode)) {
            this.contentNodes.remove(parentNode.getId());
            removeNodeFromParent(parentNode);
        }
    }

    public Collection<ContentItem> getRecent () {
        return this.recent;
    }

    private void maybeAddToRecent(final ContentItem item) {
        if (this.recentNode == null) return;
        if (item.getParentId() == null) return;  // Things like subtitles and thumbnails.

        // Do not add items in collections that require auth.
        // For safety missing content nodes are assumed to have an auth list.
        final ContentNode node = getNode(item.getParentId());
        if (node == null) {
            LOG.warn("Item {} has parent {} which is not in the content tree.", item.getId(), item.getParentId());
            return;
        }
       // if (node.hasAuthList()) return;

        if (item.getLastModified() < this.oldestRecentItem) return;

        synchronized (this.recentLock) {
            this.recent.add(item);
            if (this.recent.size() > MAX_RECENT_ITEMS) {
                this.recent.pollLast();
                this.oldestRecentItem = this.recent.last().getLastModified();
            }
            else {
                this.oldestRecentItem = 0L;
            }
        }
    }

    private void removeFromRecent(final ContentItem item) {
        if (this.recentNode == null) return;

        synchronized (this.recentLock) {
            this.recent.remove(item);
        }
    }

}