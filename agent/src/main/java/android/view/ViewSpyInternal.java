package android.view;

/**
 * Pass through to allow internal access to {@link JavaViewSpy#windowAttachCount(View)} without
 * making that class public.
 */
public class ViewSpyInternal {

    public static int windowAttachCount(View view) {
        return JavaViewSpy.windowAttachCount(view);
    }
}

