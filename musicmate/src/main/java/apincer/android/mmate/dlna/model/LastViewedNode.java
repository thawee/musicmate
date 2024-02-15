package apincer.android.mmate.dlna.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.List;

public class LastViewedNode<T extends Enum<T> & MediaFormat> extends ContainerNode {
    final Class<T> formatClass;
    final int limit;

    public LastViewedNode(@JsonProperty("title") String title,
                          @JsonProperty("format") String format,
                          @JsonProperty("limit") Integer limit) {
        super("Last-Viewed-" + idGenerator.getAndIncrement(),
                title == null || title.trim().isEmpty() ? "Last viewed " + format : title);
        this.formatClass = Formats.fromString(format);
        this.limit = limit == null ? 10 : limit.intValue();
    }

    @Override
    public List<ContainerNode> getContainers() {
        return Collections.emptyList();
    }

    @Override
    public List<ItemNode> getItems() {
        return ViewLog.getLastViewItems(limit, formatClass, getParent());
    }

    public Class<? extends MediaFormat> getFormatClass() {
        return formatClass;
    }
}