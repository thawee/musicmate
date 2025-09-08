package apincer.android.mmate.viewmodel;

public class ProgressUpdate implements OperationStatus {
    public final int progress;
    public final String currentStep;
    public ProgressUpdate(int progress, String currentStep) {
        this.progress = progress;
        this.currentStep = currentStep;
    }
}
