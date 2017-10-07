package eu.darken.bluemusic;

import android.content.Context;
import android.support.annotation.StringRes;

import javax.inject.Inject;

@AppComponent.Scope
public class ResHelper {
    private final Context context;

    @Inject
    public ResHelper(Context context) {this.context = context;}


    public String getString(@StringRes int stringRes) {
        return context.getString(stringRes);
    }
}
