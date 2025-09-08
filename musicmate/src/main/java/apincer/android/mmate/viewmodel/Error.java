package apincer.android.mmate.viewmodel;

public class Error implements OperationStatus {
    public final String errorMessage;
    public Error(String message) { this.errorMessage = message; }
}
