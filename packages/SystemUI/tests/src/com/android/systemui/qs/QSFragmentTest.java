/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package com.android.systemui.qs;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Fragment;
import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.testing.AndroidTestingRunner;
import android.testing.TestableLooper.RunWithLooper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.filters.SmallTest;

import com.android.internal.logging.UiEventLogger;
import com.android.keyguard.BouncerPanelExpansionCalculator;
import com.android.systemui.R;
import com.android.systemui.SysuiBaseFragmentTest;
import com.android.systemui.animation.ShadeInterpolation;
import com.android.systemui.broadcast.BroadcastDispatcher;
import com.android.systemui.dump.DumpManager;
import com.android.systemui.flags.FakeFeatureFlags;
import com.android.systemui.flags.Flags;
import com.android.systemui.media.MediaHost;
import com.android.systemui.plugins.FalsingManager;
import com.android.systemui.plugins.statusbar.StatusBarStateController;
import com.android.systemui.qs.customize.QSCustomizerController;
import com.android.systemui.qs.dagger.QSFragmentComponent;
import com.android.systemui.qs.external.CustomTileStatePersister;
import com.android.systemui.qs.external.TileLifecycleManager;
import com.android.systemui.qs.external.TileServiceRequestController;
import com.android.systemui.qs.footer.ui.viewmodel.FooterActionsViewModel;
import com.android.systemui.statusbar.CommandQueue;
import com.android.systemui.statusbar.StatusBarState;
import com.android.systemui.statusbar.phone.AutoTileManager;
import com.android.systemui.statusbar.phone.CentralSurfaces;
import com.android.systemui.statusbar.phone.KeyguardBypassController;
import com.android.systemui.statusbar.phone.StatusBarIconController;
import com.android.systemui.statusbar.policy.ConfigurationController;
import com.android.systemui.statusbar.policy.RemoteInputQuickSettingsDisabler;
import com.android.systemui.tuner.TunerService;
import com.android.systemui.util.animation.UniqueObjectHostView;
import com.android.systemui.util.settings.SecureSettings;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Optional;

@RunWith(AndroidTestingRunner.class)
@RunWithLooper(setAsMainLooper = true)
@SmallTest
public class QSFragmentTest extends SysuiBaseFragmentTest {

    @Mock private QSFragmentComponent.Factory mQsComponentFactory;
    @Mock private QSFragmentComponent mQsFragmentComponent;
    @Mock private QSPanelController mQSPanelController;
    @Mock private MediaHost mQSMediaHost;
    @Mock private MediaHost mQQSMediaHost;
    @Mock private KeyguardBypassController mBypassController;
    @Mock private FalsingManager mFalsingManager;
    @Mock private TileServiceRequestController.Builder mTileServiceRequestControllerBuilder;
    @Mock private TileServiceRequestController mTileServiceRequestController;
    @Mock private QSCustomizerController mQsCustomizerController;
    @Mock private QuickQSPanelController mQuickQSPanelController;
    @Mock private FooterActionsController mQSFooterActionController;
    @Mock private QSContainerImplController mQSContainerImplController;
    @Mock private QSContainerImpl mContainer;
    @Mock private QSFooter mFooter;
    @Mock private LayoutInflater mLayoutInflater;
    @Mock private NonInterceptingScrollView mQSPanelScrollView;
    @Mock private QuickStatusBarHeader mHeader;
    @Mock private QSPanel.QSTileLayout mQsTileLayout;
    @Mock private QSPanel.QSTileLayout mQQsTileLayout;
    @Mock private QSAnimator mQSAnimator;
    @Mock private StatusBarStateController mStatusBarStateController;
    @Mock private QSSquishinessController mSquishinessController;
    private View mQsFragmentView;

    public QSFragmentTest() {
        super(QSFragment.class);
    }

    @Before
    public void setup() {
        injectLeakCheckedDependencies(ALL_SUPPORTED_CLASSES);
    }

    @Test
    public void testListening() {
        QSFragment qs = (QSFragment) mFragment;
        mFragments.dispatchResume();
        processAllMessages();

        QSTileHost host =
                new QSTileHost(
                        mContext,
                        mock(StatusBarIconController.class),
                        mock(QSFactoryImpl.class),
                        new Handler(),
                        Looper.myLooper(),
                        mock(PluginManager.class),
                        mock(TunerService.class),
                        () -> mock(AutoTileManager.class),
                        mock(DumpManager.class),
                        mock(BroadcastDispatcher.class),
                        Optional.of(mock(CentralSurfaces.class)),
                        mock(QSLogger.class),
                        mock(UiEventLogger.class),
                        mock(UserTracker.class),
                        mock(SecureSettings.class),
                        mock(CustomTileStatePersister.class),
                        mTileServiceRequestControllerBuilder,
                        mock(TileLifecycleManager.Factory.class));

        qs.setListening(true);
        processAllMessages();

        qs.setListening(false);
        processAllMessages();
        host.destroy();
        processAllMessages();
    }

    @Test
    public void testSaveState() {
        mFragments.dispatchResume();
        processAllMessages();

        QSFragment qs = (QSFragment) mFragment;
        qs.setListening(true);
        qs.setExpanded(true);
        qs.setQsVisible(true);
        processAllMessages();
        recreateFragment();
        processAllMessages();

        // Get the reference to the new fragment.
        qs = (QSFragment) mFragment;
        assertTrue(qs.isListening());
        assertTrue(qs.isExpanded());
        assertTrue(qs.isQsVisible());
    }

    @Test
    public void transitionToFullShade_setsAlphaUsingShadeInterpolator() {
        QSFragment fragment = resumeAndGetFragment();
        setStatusBarState(StatusBarState.SHADE);
        boolean isTransitioningToFullShade = true;
        float transitionProgress = 0.5f;
        float squishinessFraction = 0.5f;

        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);

        assertThat(mQsFragmentView.getAlpha())
                .isEqualTo(ShadeInterpolation.getContentAlpha(transitionProgress));
    }

    @Test
    public void
            transitionToFullShade_onKeyguard_noBouncer_setsAlphaUsingLinearInterpolator() {
        QSFragment fragment = resumeAndGetFragment();
        setStatusBarState(StatusBarState.KEYGUARD);
        when(mQSPanelController.isBouncerInTransit()).thenReturn(false);
        boolean isTransitioningToFullShade = true;
        float transitionProgress = 0.5f;
        float squishinessFraction = 0.5f;

        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);

        assertThat(mQsFragmentView.getAlpha()).isEqualTo(transitionProgress);
    }

    @Test
    public void
            transitionToFullShade_onKeyguard_bouncerActive_setsAlphaUsingBouncerInterpolator() {
        QSFragment fragment = resumeAndGetFragment();
        setStatusBarState(StatusBarState.KEYGUARD);
        when(mQSPanelController.isBouncerInTransit()).thenReturn(true);
        boolean isTransitioningToFullShade = true;
        float transitionProgress = 0.5f;
        float squishinessFraction = 0.5f;

        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);

        assertThat(mQsFragmentView.getAlpha())
                .isEqualTo(
                        BouncerPanelExpansionCalculator.aboutToShowBouncerProgress(
                                transitionProgress));
    }

    @Test
    public void transitionToFullShade_inFullWidth_alwaysSetsAlphaTo1() {
        QSFragment fragment = resumeAndGetFragment();
        fragment.setIsNotificationPanelFullWidth(true);

        boolean isTransitioningToFullShade = true;
        float transitionProgress = 0.1f;
        float squishinessFraction = 0.5f;
        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);
        assertThat(mQsFragmentView.getAlpha()).isEqualTo(1);

        transitionProgress = 0.5f;
        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);
        assertThat(mQsFragmentView.getAlpha()).isEqualTo(1);
        assertThat(mQsFragmentView.getAlpha()).isEqualTo(1);

        transitionProgress = 0.7f;
        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);
        assertThat(mQsFragmentView.getAlpha()).isEqualTo(1);
    }

    @Test
    public void transitionToFullShade_setsSquishinessOnController() {
        QSFragment fragment = resumeAndGetFragment();
        boolean isTransitioningToFullShade = true;
        float transitionProgress = 0.123f;
        float squishinessFraction = 0.456f;

        fragment.setTransitionToFullShadeProgress(isTransitioningToFullShade, transitionProgress,
                squishinessFraction);

        verify(mQsFragmentComponent.getQSSquishinessController())
                .setSquishiness(squishinessFraction);
    }

    @Test
    public void setQsExpansion_inSplitShade_setsFooterActionsExpansion_basedOnPanelExpFraction() {
        // Random test values without any meaning. They just have to be different from each other.
        float expansion = 0.123f;
        float panelExpansionFraction = 0.321f;
        float proposedTranslation = 456f;
        float squishinessFraction = 0.987f;

        QSFragment fragment = resumeAndGetFragment();
        enableSplitShade();

        fragment.setQsExpansion(expansion, panelExpansionFraction, proposedTranslation,
                squishinessFraction);

        verify(mQSFooterActionController).setExpansion(panelExpansionFraction);
    }

    @Test
    public void setQsExpansion_notInSplitShade_setsFooterActionsExpansion_basedOnExpansion() {
        // Random test values without any meaning. They just have to be different from each other.
        float expansion = 0.123f;
        float panelExpansionFraction = 0.321f;
        float proposedTranslation = 456f;
        float squishinessFraction = 0.987f;

        QSFragment fragment = resumeAndGetFragment();
        disableSplitShade();

        fragment.setQsExpansion(expansion, panelExpansionFraction, proposedTranslation,
                squishinessFraction);

        verify(mQSFooterActionController).setExpansion(expansion);
    }

    @Test
    public void getQsMinExpansionHeight_notInSplitShade_returnsHeaderHeight() {
        QSFragment fragment = resumeAndGetFragment();
        disableSplitShade();
        when(mHeader.getHeight()).thenReturn(1234);

        int height = fragment.getQsMinExpansionHeight();

        assertThat(height).isEqualTo(mHeader.getHeight());
    }

    @Test
    public void getQsMinExpansionHeight_inSplitShade_returnsAbsoluteBottomOfQSContainer() {
        int top = 1234;
        int height = 9876;
        QSFragment fragment = resumeAndGetFragment();
        enableSplitShade();
        setLocationOnScreen(mQsFragmentView, top);
        when(mQsFragmentView.getHeight()).thenReturn(height);

        int expectedHeight = top + height;
        assertThat(fragment.getQsMinExpansionHeight()).isEqualTo(expectedHeight);
    }

    @Test
    public void getQsMinExpansionHeight_inSplitShade_returnsAbsoluteBottomExcludingTranslation() {
        int top = 1234;
        int height = 9876;
        float translationY = -600f;
        QSFragment fragment = resumeAndGetFragment();
        enableSplitShade();
        setLocationOnScreen(mQsFragmentView, (int) (top + translationY));
        when(mQsFragmentView.getHeight()).thenReturn(height);
        when(mQsFragmentView.getTranslationY()).thenReturn(translationY);

        int expectedHeight = top + height;
        assertThat(fragment.getQsMinExpansionHeight()).isEqualTo(expectedHeight);
    }

    @Test
    public void hideImmediately_notInSplitShade_movesViewUpByHeaderHeight() {
        QSFragment fragment = resumeAndGetFragment();
        disableSplitShade();
        when(mHeader.getHeight()).thenReturn(555);

        fragment.hideImmediately();

        assertThat(mQsFragmentView.getY()).isEqualTo(-mHeader.getHeight());
    }

    @Test
    public void hideImmediately_inSplitShade_movesViewUpByQSAbsoluteBottom() {
        QSFragment fragment = resumeAndGetFragment();
        enableSplitShade();
        int top = 1234;
        int height = 9876;
        setLocationOnScreen(mQsFragmentView, top);
        when(mQsFragmentView.getHeight()).thenReturn(height);

        fragment.hideImmediately();

        int qsAbsoluteBottom = top + height;
        assertThat(mQsFragmentView.getY()).isEqualTo(-qsAbsoluteBottom);
    }

    @Test
    public void setCollapseExpandAction_passedToControllers() {
        Runnable action = () -> {};
        QSFragment fragment = resumeAndGetFragment();
        fragment.setCollapseExpandAction(action);

        verify(mQSPanelController).setCollapseExpandAction(action);
        verify(mQuickQSPanelController).setCollapseExpandAction(action);
    }

    @Test
    public void setOverScrollAmount_setsTranslationOnView() {
        QSFragment fragment = resumeAndGetFragment();

        fragment.setOverScrollAmount(123);

        assertThat(mQsFragmentView.getTranslationY()).isEqualTo(123);
    }

    @Test
    public void setOverScrollAmount_beforeViewCreated_translationIsNotSet() {
        QSFragment fragment = getFragment();

        fragment.setOverScrollAmount(123);

        assertThat(mQsFragmentView.getTranslationY()).isEqualTo(0);
    }

    @Test
    public void setListeningFalse_notVisible() {
        QSFragment fragment = resumeAndGetFragment();
        fragment.setQsVisible(false);
        clearInvocations(mQSContainerImplController, mQSPanelController, mQSFooterActionController);

        fragment.setListening(false);
        verify(mQSContainerImplController).setListening(false);
        verify(mQSFooterActionController).setListening(false);
        verify(mQSPanelController).setListening(eq(false), anyBoolean());
    }

    @Test
    public void setListeningTrue_notVisible() {
        QSFragment fragment = resumeAndGetFragment();
        fragment.setQsVisible(false);
        clearInvocations(mQSContainerImplController, mQSPanelController, mQSFooterActionController);

        fragment.setListening(true);
        verify(mQSContainerImplController).setListening(false);
        verify(mQSFooterActionController).setListening(false);
        verify(mQSPanelController).setListening(eq(false), anyBoolean());
    }

    @Test
    public void setListeningFalse_visible() {
        QSFragment fragment = resumeAndGetFragment();
        fragment.setQsVisible(true);
        clearInvocations(mQSContainerImplController, mQSPanelController, mQSFooterActionController);

        fragment.setListening(false);
        verify(mQSContainerImplController).setListening(false);
        verify(mQSFooterActionController).setListening(false);
        verify(mQSPanelController).setListening(eq(false), anyBoolean());
    }

    @Test
    public void setListeningTrue_visible() {
        QSFragment fragment = resumeAndGetFragment();
        fragment.setQsVisible(true);
        clearInvocations(mQSContainerImplController, mQSPanelController, mQSFooterActionController);

        fragment.setListening(true);
        verify(mQSContainerImplController).setListening(true);
        verify(mQSFooterActionController).setListening(true);
        verify(mQSPanelController).setListening(eq(true), anyBoolean());
    }

    @Override
    protected Fragment instantiate(Context context, String className, Bundle arguments) {
        MockitoAnnotations.initMocks(this);
        CommandQueue commandQueue = new CommandQueue(context);

        setupQsComponent();
        setUpViews();
        setUpInflater();
        setUpMedia();
        setUpOther();

        FakeFeatureFlags featureFlags = new FakeFeatureFlags();
        featureFlags.set(Flags.NEW_FOOTER_ACTIONS, false);
        return new QSFragment(
                new RemoteInputQuickSettingsDisabler(
                        context, commandQueue, mock(ConfigurationController.class)),
                mock(QSTileHost.class),
                mStatusBarStateController,
                commandQueue,
                mQSMediaHost,
                mQQSMediaHost,
                mBypassController,
                mQsComponentFactory,
                mock(QSFragmentDisableFlagsLogger.class),
                mFalsingManager,
                mock(DumpManager.class),
                featureFlags,
                mock(NewFooterActionsController.class),
                mock(FooterActionsViewModel.Factory.class));
    }

    private void setUpOther() {
        when(mTileServiceRequestControllerBuilder.create(any()))
                .thenReturn(mTileServiceRequestController);
        when(mQSContainerImplController.getView()).thenReturn(mContainer);
        when(mQSPanelController.getTileLayout()).thenReturn(mQQsTileLayout);
        when(mQuickQSPanelController.getTileLayout()).thenReturn(mQsTileLayout);
    }

    private void setUpMedia() {
        when(mQSMediaHost.getCurrentClipping()).thenReturn(new Rect());
        when(mQSMediaHost.getHostView()).thenReturn(new UniqueObjectHostView(mContext));
        when(mQQSMediaHost.getHostView()).thenReturn(new UniqueObjectHostView(mContext));
    }

    private void setUpViews() {
        mQsFragmentView = spy(new View(mContext));
        when(mQsFragmentView.findViewById(R.id.expanded_qs_scroll_view))
                .thenReturn(mQSPanelScrollView);
        when(mQsFragmentView.findViewById(R.id.header)).thenReturn(mHeader);
        when(mQsFragmentView.findViewById(android.R.id.edit)).thenReturn(new View(mContext));
    }

    private void setUpInflater() {
        when(mLayoutInflater.cloneInContext(any(Context.class))).thenReturn(mLayoutInflater);
        when(mLayoutInflater.inflate(anyInt(), any(ViewGroup.class), anyBoolean()))
                .thenReturn(mQsFragmentView);
        mContext.addMockSystemService(Context.LAYOUT_INFLATER_SERVICE, mLayoutInflater);
    }

    private void setupQsComponent() {
        when(mQsComponentFactory.create(any(QSFragment.class))).thenReturn(mQsFragmentComponent);
        when(mQsFragmentComponent.getQSPanelController()).thenReturn(mQSPanelController);
        when(mQsFragmentComponent.getQuickQSPanelController()).thenReturn(mQuickQSPanelController);
        when(mQsFragmentComponent.getQSCustomizerController()).thenReturn(mQsCustomizerController);
        when(mQsFragmentComponent.getQSContainerImplController())
                .thenReturn(mQSContainerImplController);
        when(mQsFragmentComponent.getQSFooter()).thenReturn(mFooter);
        when(mQsFragmentComponent.getQSFooterActionController())
                .thenReturn(mQSFooterActionController);
        when(mQsFragmentComponent.getQSAnimator()).thenReturn(mQSAnimator);
        when(mQsFragmentComponent.getQSSquishinessController()).thenReturn(mSquishinessController);
    }

    private QSFragment getFragment() {
        return ((QSFragment) mFragment);
    }

    private QSFragment resumeAndGetFragment() {
        mFragments.dispatchResume();
        processAllMessages();
        return getFragment();
    }

    private void setStatusBarState(int statusBarState) {
        when(mStatusBarStateController.getState()).thenReturn(statusBarState);
        getFragment().onStateChanged(statusBarState);
    }

    private void enableSplitShade() {
        setSplitShadeEnabled(true);
    }

    private void disableSplitShade() {
        setSplitShadeEnabled(false);
    }

    private void setSplitShadeEnabled(boolean enabled) {
        getFragment().setInSplitShade(enabled);
    }

    private void setLocationOnScreen(View view, int top) {
        doAnswer(invocation -> {
            int[] locationOnScreen = invocation.getArgument(/* index= */ 0);
            locationOnScreen[0] = 0;
            locationOnScreen[1] = top;
            return null;
        }).when(view).getLocationOnScreen(any(int[].class));
    }
}
