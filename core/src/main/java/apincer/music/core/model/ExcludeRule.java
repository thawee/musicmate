package apincer.music.core.model;
import java.util.List;

public class ExcludeRule {
    private List<String> mood;
    private List<String> style;

    public List<String> getMood() { return mood; }
    public void setMood(List<String> mood) { this.mood = mood; }

    public List<String> getStyle() { return style; }
    public void setStyle(List<String> style) { this.style = style; }

    public boolean isEmpty() {
        return mood ==null && style == null;
    }
}