package apincer.android.mmate.utils;

public interface ExConsumer<I, E extends Exception> {

    void accept(I input) throws E;

}