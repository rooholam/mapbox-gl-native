package com.mapbox.mapboxsdk.snapshotter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.util.DisplayMetrics;

import com.mapbox.mapboxsdk.R;
import com.mapbox.mapboxsdk.camera.CameraPosition;
import com.mapbox.mapboxsdk.constants.Style;
import com.mapbox.mapboxsdk.geometry.LatLngBounds;
import com.mapbox.mapboxsdk.storage.FileSource;

/**
 * The map snapshotter creates a bitmap of the map, rendered
 * off the UI thread. The snapshotter itself must be used on
 * the UI thread (for access to the main looper)
 */
@UiThread
public class MapSnapshotter {

  /**
   * Get notified on snapshot completion.
   *
   * @see MapSnapshotter#start(SnapshotReadyCallback, ErrorHandler)
   */
  public interface SnapshotReadyCallback {

    /**
     * Called when the snapshot is complete.
     *
     * @param snapshot the snapshot
     */
    void onSnapshotReady(MapSnapshot snapshot);

  }

  /**
   * Can be used to get notified of errors
   * in snapshot generation
   *
   * @see MapSnapshotter#start(SnapshotReadyCallback, ErrorHandler)
   */
  public interface ErrorHandler {

    /**
     * Called on error. Snapshotting will not
     * continue
     *
     * @param error the error message
     */
    void onError(String error);
  }

  private static final int LOGO_MARGIN_DP = 4;

  // Holds the pointer to JNI NativeMapView
  private long nativePtr = 0;

  private final Context context;
  private SnapshotReadyCallback callback;
  private ErrorHandler errorHandler;

  /**
   * MapSnapshotter options
   */
  public static class Options {
    private int pixelRatio = 1;
    private int width;
    private int height;
    private String styleUrl = Style.MAPBOX_STREETS;
    private LatLngBounds region;
    private CameraPosition cameraPosition;
    private boolean showLogo = true;

    /**
     * @param width  the width of the image
     * @param height the height of the image
     */
    public Options(int width, int height) {
      this.width = width;
      this.height = height;
    }

    /**
     * @param url The style URL to use
     * @return the mutated {@link Options}
     */
    public Options withStyle(String url) {
      this.styleUrl = url;
      return this;
    }

    /**
     * @param region the region to show in the snapshot.
     *               This is applied after the camera position
     * @return the mutated {@link Options}
     */
    public Options withRegion(LatLngBounds region) {
      this.region = region;
      return this;
    }

    /**
     * @param pixelRatio the pixel ratio to use (default: 1)
     * @return the mutated {@link Options}
     */
    public Options withPixelRatio(int pixelRatio) {
      this.pixelRatio = pixelRatio;
      return this;
    }

    /**
     * @param cameraPosition The camera position to use,
     *                       the {@link CameraPosition#target} is overridden
     *                       by region if set in conjunction.
     * @return the mutated {@link Options}
     */
    public Options withCameraPosition(CameraPosition cameraPosition) {
      this.cameraPosition = cameraPosition;
      return this;
    }

    /**
     * @param showLogo The flag indicating to show the Mapbox logo.
     * @return the mutated {@link Options}
     */
    public Options withLogo(boolean showLogo) {
      this.showLogo = showLogo;
      return this;
    }

    /**
     * @return the width of the image
     */
    public int getWidth() {
      return width;
    }

    /**
     * @return the height of the image
     */
    public int getHeight() {
      return height;
    }

    /**
     * @return the pixel ratio
     */
    public int getPixelRatio() {
      return pixelRatio;
    }

    /**
     * @return the region
     */
    @Nullable
    public LatLngBounds getRegion() {
      return region;
    }

    /**
     * @return the style url
     */
    public String getStyleUrl() {
      return styleUrl;
    }

    /**
     * @return the camera position
     */
    @Nullable
    public CameraPosition getCameraPosition() {
      return cameraPosition;
    }
  }

  /**
   * Creates the Map snapshotter, but doesn't start rendering or
   * loading yet.
   *
   * @param context the Context that is or contains the Application context
   * @param options the options to use for the snapshot
   */
  public MapSnapshotter(@NonNull Context context, @NonNull Options options) {
    this.context = context.getApplicationContext();
    FileSource fileSource = FileSource.getInstance(context);
    String programCacheDir = context.getCacheDir().getAbsolutePath();

    nativeInitialize(this, fileSource, options.pixelRatio, options.width,
      options.height, options.styleUrl, options.region, options.cameraPosition,
      options.showLogo, programCacheDir);
  }

  /**
   * Starts loading and rendering the snapshot. The callback will be fired
   * on the calling thread.
   *
   * @param callback the callback to use when the snapshot is ready
   */
  public void start(@NonNull SnapshotReadyCallback callback) {
    this.start(callback, null);
  }

  /**
   * Starts loading and rendering the snapshot. The callbacks will be fired
   * on the calling thread.
   *
   * @param callback     the callback to use when the snapshot is ready
   * @param errorHandler the error handler to use on snapshot errors
   */
  public void start(@NonNull SnapshotReadyCallback callback, ErrorHandler errorHandler) {
    if (this.callback != null) {
      throw new IllegalStateException("Snapshotter was already started");
    }

    this.callback = callback;
    this.errorHandler = errorHandler;
    nativeStart();
  }

  /**
   * Updates the snapshotter with a new size
   *
   * @param width  the width
   * @param height the height
   */
  public native void setSize(int width, int height);

  /**
   * Updates the snapshotter with a new {@link CameraPosition}
   *
   * @param cameraPosition the camera position
   */
  public native void setCameraPosition(CameraPosition cameraPosition);

  /**
   * Updates the snapshotter with a new {@link LatLngBounds}
   *
   * @param region the region
   */
  public native void setRegion(LatLngBounds region);

  /**
   * Updates the snapshotter with a new style url
   *
   * @param styleUrl the style url
   */
  public native void setStyleUrl(String styleUrl);


  /**
   * Must be called in on the thread
   * the object was created on.
   */
  public void cancel() {
    reset();
    nativeCancel();
  }

  protected void addOverlay(Bitmap original) {
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    float margin = displayMetrics.density * LOGO_MARGIN_DP;
    Canvas canvas = new Canvas(original);

    // Decode just the boundaries
    BitmapFactory.Options bitmapOptions = new BitmapFactory.Options();
    bitmapOptions.inJustDecodeBounds = true;
    BitmapFactory.decodeResource(context.getResources(), R.drawable.mapbox_logo_icon, bitmapOptions);
    int srcWidth = bitmapOptions.outWidth;
    int srcHeight = bitmapOptions.outHeight;

    // Ratio, preferred dimensions and resulting sample size
    float widthRatio = displayMetrics.widthPixels / original.getWidth();
    float heightRatio = displayMetrics.heightPixels / original.getHeight();
    float prefWidth = srcWidth / widthRatio;
    float prefHeight = srcHeight / heightRatio;
    int sampleSize = MapSnaphotUtil.calculateInSampleSize(bitmapOptions, (int) prefWidth, (int) prefHeight);

    // Scale bitmap
    bitmapOptions.inJustDecodeBounds = false;
    bitmapOptions.inScaled = true;
    bitmapOptions.inSampleSize = sampleSize;
    bitmapOptions.inDensity = srcWidth;
    bitmapOptions.inTargetDensity = (int) prefWidth * bitmapOptions.inSampleSize;
    Bitmap logo = BitmapFactory.decodeResource(context.getResources(), R.drawable.mapbox_logo_icon, bitmapOptions);

    float scaledHeight = bitmapOptions.outHeight * heightRatio / bitmapOptions.inSampleSize;
    canvas.drawBitmap(logo, margin, original.getHeight() - scaledHeight - margin, null);
  }

  /**
   * Called by JNI peer when snapshot is ready.
   * Always called on the origin (main) thread.
   *
   * @param snapshot the generated snapshot
   */
  protected void onSnapshotReady(MapSnapshot snapshot) {
    if (callback != null) {
      if (snapshot.isShowLogo()) {
        addOverlay(snapshot.getBitmap());
      }
      callback.onSnapshotReady(snapshot);
      reset();
    }
  }

  /**
   * Called by JNI peer when snapshot has failed.
   * Always called on the origin (main) thread.
   *
   * @param reason the exception string
   */
  protected void onSnapshotFailed(String reason) {
    if (errorHandler != null) {
      errorHandler.onError(reason);
      reset();
    }
  }

  protected void reset() {
    callback = null;
    errorHandler = null;
  }

  protected native void nativeInitialize(MapSnapshotter mapSnapshotter,
                                         FileSource fileSource, float pixelRatio,
                                         int width, int height, String styleUrl,
                                         LatLngBounds region, CameraPosition position,
                                         boolean showLogo, String programCacheDir);

  protected native void nativeStart();

  protected native void nativeCancel();

  @Override
  protected native void finalize() throws Throwable;
}
