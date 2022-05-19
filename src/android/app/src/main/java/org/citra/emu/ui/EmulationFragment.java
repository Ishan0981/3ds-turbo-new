package org.citra.emu.ui;

import static android.os.Looper.getMainLooper;

import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import org.citra.emu.NativeLibrary;
import org.citra.emu.R;
import org.citra.emu.overlay.InputOverlay;
import org.citra.emu.utils.DirectoryInitialization;
import org.citra.emu.utils.DirectoryInitialization.DirectoryInitializationState;
import org.citra.emu.utils.DirectoryInitialization.DirectoryStateReceiver;
import org.citra.emu.utils.Log;

import java.util.ArrayList;
import java.util.List;

public final class EmulationFragment extends Fragment implements SurfaceHolder.Callback, Choreographer.FrameCallback {
    private static final String KEY_GAMEPATH = "gamepath";

    private SharedPreferences mPreferences;

    private InputOverlay mInputOverlay;

    private EmulationState mEmulationState;

    private DirectoryStateReceiver directoryStateReceiver;

    private EmulationActivity mActivity;

    private TextView mNetPlayMessage;

    private List<String> mMessageList;

    private Handler mTaskHandler;

    public static EmulationFragment newInstance(String gamePath) {
        Bundle args = new Bundle();
        args.putString(KEY_GAMEPATH, gamePath);

        EmulationFragment fragment = new EmulationFragment();
        fragment.setArguments(args);
        return fragment;
    }

    public void refreshControls() {
        mInputOverlay.refreshControls();
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);

        if (context instanceof EmulationActivity) {
            mActivity = (EmulationActivity) context;
            NativeLibrary.setEmulationActivity((EmulationActivity) context);
        } else {
            throw new IllegalStateException("EmulationFragment must have EmulationActivity parent");
        }
    }

    /**
     * Initialize anything that doesn't depend on the layout / views in here.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // So this fragment doesn't restart on configuration changes; i.e. rotation.
        setRetainInstance(true);

        mPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());

        String gamePath = getArguments().getString(KEY_GAMEPATH);
        mEmulationState = new EmulationState(gamePath);
    }

    /**
     * Initialize the UI and start emulation in here.
     */
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View contents = inflater.inflate(R.layout.fragment_emulation, container, false);

        SurfaceView surfaceView = contents.findViewById(R.id.surface_emulation);
        surfaceView.getHolder().addCallback(this);

        mInputOverlay = contents.findViewById(R.id.surface_input_overlay);
        mNetPlayMessage = contents.findViewById(R.id.netplay_message);

        Button doneButton = contents.findViewById(R.id.done_control_config);
        if (doneButton != null) {
            doneButton.setOnClickListener(v -> stopConfiguringControls());
        }

        // The new Surface created here will get passed to the native code via onSurfaceChanged.
        return contents;
    }

    @Override
    public void onResume() {
        super.onResume();
        Choreographer.getInstance().postFrameCallback(this);
        if (DirectoryInitialization.areCitraDirectoriesReady()) {
            mEmulationState.run(mActivity.isActivityRecreated());
        } else {
            setupCitraDirectoriesThenStartEmulation();
        }
    }

    @Override
    public void onPause() {
        if (directoryStateReceiver != null) {
            LocalBroadcastManager.getInstance(getActivity()).unregisterReceiver(directoryStateReceiver);
            directoryStateReceiver = null;
        }

        if (mEmulationState.isRunning()) {
            mEmulationState.pause();
        }

        Choreographer.getInstance().removeFrameCallback(this);
        super.onPause();
    }

    @Override
    public void onDetach() {
        NativeLibrary.clearEmulationActivity();
        super.onDetach();
    }

    private void setupCitraDirectoriesThenStartEmulation() {
        IntentFilter statusIntentFilter = new IntentFilter(
                DirectoryInitialization.BROADCAST_ACTION);

        directoryStateReceiver =
                new DirectoryStateReceiver(directoryInitializationState ->
                {
                    if (directoryInitializationState ==
                            DirectoryInitializationState.CITRA_DIRECTORIES_INITIALIZED) {
                        mEmulationState.run(mActivity.isActivityRecreated());
                    } else if (directoryInitializationState ==
                            DirectoryInitializationState.EXTERNAL_STORAGE_PERMISSION_NEEDED) {
                        Toast.makeText(getContext(), R.string.write_permission_needed, Toast.LENGTH_SHORT)
                                .show();
                    } else if (directoryInitializationState ==
                            DirectoryInitializationState.CANT_FIND_EXTERNAL_STORAGE) {
                        Toast.makeText(getContext(), R.string.external_storage_not_mounted,
                                Toast.LENGTH_SHORT)
                                .show();
                    }
                });

        // Registers the DirectoryStateReceiver and its intent filters
        LocalBroadcastManager.getInstance(getActivity()).registerReceiver(
                directoryStateReceiver,
                statusIntentFilter);
        DirectoryInitialization.start(getActivity());
    }

    public void refreshInputOverlay() {
        mInputOverlay.refreshControls();
    }

    public void resetInputOverlay() {
        // Reset button scale
        SharedPreferences.Editor editor = mPreferences.edit();
        editor.putInt(InputOverlay.PREF_CONTROLLER_SCALE, 50);
        editor.putInt(InputOverlay.PREF_CONTROLLER_ALPHA, 100);
        editor.apply();

        mInputOverlay.resetButtonPlacement();
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        // We purposely don't do anything here.
        // All work is done in surfaceChanged, which we are guaranteed to get even for surface creation.
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.debug("[EmulationFragment] Surface changed. Resolution: " + width + "x" + height);
        mEmulationState.newSurface(holder.getSurface());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        mEmulationState.clearSurface();
    }

    @Override
    public void doFrame(long frameTimeNanos) {
        Choreographer.getInstance().postFrameCallback(this);
        NativeLibrary.DoFrame();
    }

    public void stopEmulation() {
        mEmulationState.stop();
    }

    public void startConfiguringControls() {
        getView().findViewById(R.id.done_control_config).setVisibility(View.VISIBLE);
        mInputOverlay.setIsInEditMode(true);
    }

    public void stopConfiguringControls() {
        getView().findViewById(R.id.done_control_config).setVisibility(View.GONE);
        mInputOverlay.setIsInEditMode(false);
    }

    public void addNetPlayMessage(String msg) {
        if (msg.isEmpty()) {
            return;
        }

        if (mMessageList == null) {
            mMessageList = new ArrayList<>();
            mTaskHandler = new Handler(getMainLooper());
        }
        mMessageList.add(msg);
        if (mMessageList.size() > 10) {
            mMessageList.remove(0);
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mMessageList.size(); ++i) {
            sb.append(mMessageList.get(i));
            sb.append(System.lineSeparator());
        }
        mNetPlayMessage.setText(sb.toString());
        mNetPlayMessage.setVisibility(View.VISIBLE);

        mTaskHandler.removeCallbacksAndMessages(null);
        mTaskHandler.postDelayed(() -> {
            mNetPlayMessage.setVisibility(View.INVISIBLE);
            if (mMessageList != null) {
                mMessageList.clear();
            }
        }, 6 * 1000);
    }

    public boolean isConfiguringControls() {
        return mInputOverlay.isInEditMode();
    }

    private static class EmulationState {
        private final String mGamePath;
        private State state;
        private Surface mSurface;
        private boolean mRunWhenSurfaceIsValid;

        EmulationState(String gamePath) {
            mGamePath = gamePath;
            // Starting state is stopped.
            state = State.STOPPED;
        }

        public synchronized boolean isStopped() {
            return state == State.STOPPED;
        }

        // Getters for the current state

        public synchronized boolean isPaused() {
            return state == State.PAUSED;
        }

        public synchronized boolean isRunning() {
            return state == State.RUNNING;
        }

        public synchronized void stop() {
            if (state != State.STOPPED) {
                Log.debug("[EmulationFragment] Stopping emulation.");
                state = State.STOPPED;
                NativeLibrary.StopEmulation();
            } else {
                Log.warning("[EmulationFragment] Stop called while already stopped.");
            }
        }

        // State changing methods

        public synchronized void pause() {
            if (state != State.PAUSED) {
                state = State.PAUSED;
                Log.debug("[EmulationFragment] Pausing emulation.");

                // Release the surface before pausing, since emulation has to be running for that.
                NativeLibrary.SurfaceDestroyed();
                NativeLibrary.PauseEmulation();
            } else {
                Log.warning("[EmulationFragment] Pause called while already paused.");
            }
        }

        public synchronized void run(boolean isActivityRecreated) {
            if (isActivityRecreated) {
                if (NativeLibrary.IsRunning()) {
                    state = State.PAUSED;
                }
            } else {
                Log.debug("[EmulationFragment] activity resumed or fresh start");
            }

            // If the surface is set, run now. Otherwise, wait for it to get set.
            if (mSurface != null) {
                runWithValidSurface();
            } else {
                mRunWhenSurfaceIsValid = true;
            }
        }

        // Surface callbacks
        public synchronized void newSurface(Surface surface) {
            mSurface = surface;
            if (mRunWhenSurfaceIsValid) {
                runWithValidSurface();
            }
        }

        public synchronized void clearSurface() {
            if (mSurface == null) {
                Log.warning("[EmulationFragment] clearSurface called, but surface already null.");
            } else {
                mSurface = null;
                Log.debug("[EmulationFragment] Surface destroyed.");

                if (state == State.RUNNING) {
                    NativeLibrary.SurfaceDestroyed();
                    state = State.PAUSED;
                } else if (state == State.PAUSED) {
                    Log.warning("[EmulationFragment] Surface cleared while emulation paused.");
                } else {
                    Log.warning("[EmulationFragment] Surface cleared while emulation stopped.");
                }
            }
        }

        private void runWithValidSurface() {
            mRunWhenSurfaceIsValid = false;
            if (state == State.STOPPED) {
                NativeLibrary.SurfaceChanged(mSurface);
                Thread mEmulationThread = new Thread(() ->
                {
                    Log.debug("[EmulationFragment] Starting emulation thread.");
                    NativeLibrary.Run(mGamePath);
                }, "NativeEmulation");
                mEmulationThread.start();

            } else if (state == State.PAUSED) {
                Log.debug("[EmulationFragment] Resuming emulation.");
                NativeLibrary.SurfaceChanged(mSurface);
                NativeLibrary.UnPauseEmulation();
            } else {
                Log.debug("[EmulationFragment] Bug, run called while already running.");
            }
            state = State.RUNNING;
        }

        private enum State {
            STOPPED, RUNNING, PAUSED
        }
    }
}
