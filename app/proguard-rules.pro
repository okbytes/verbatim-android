# Keep ViewModel constructor instantiated by AndroidX ViewModelProvider.
-keepclassmembers class app.verbatim.VerbatimViewModel {
    public <init>(android.app.Application, androidx.lifecycle.SavedStateHandle);
}
