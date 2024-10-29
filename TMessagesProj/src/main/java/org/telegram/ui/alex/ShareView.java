package org.telegram.ui.alex;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.TimeInterpolator;
import android.animation.ValueAnimator;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Region;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.animation.LinearInterpolator;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ChatObject;
import org.telegram.messenger.ContactsController;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.alexgor.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.LaunchActivity;

import java.util.ArrayList;
import java.util.HashMap;

public class ShareView extends ViewGroup
{
    private static ShareView instance;

    private static final TimeInterpolator endShowInterpolator = new CubicBezierInterpolator(0.25f, 0.65f, 0.65f, 0.25f);
    private static final TimeInterpolator backColorInterpolator = new CubicBezierInterpolator(0.17f, 0.28f, 0.32f, 0.72f);
    private static final TimeInterpolator linearInterpolator = new LinearInterpolator();

    private static final TimeInterpolator horBoundsInterpolator = new CubicBezierInterpolator(0.18, 0.16f, 0.82f, 0.04f);
    private static final TimeInterpolator verBoundsInterpolator = new CubicBezierInterpolator(0.18f, 0.51f, 0.30f, 0.70f);

    private static final TimeInterpolator xImageInterpolator = new CubicBezierInterpolator(0.27, 0.67f, 0.44f, 0.80f);
    private static final TimeInterpolator yImageInterpolator = new CubicBezierInterpolator(0.42, 0.13f, 0.61f, 0.32f);

    private static final TimeInterpolator cellOffsetXInterpolator = new CubicBezierInterpolator(0.43, 0.27f, 0.61f, 0.34f);

    private static final float rightOffset = AndroidUtilities.dp(2);
    private static final float translateTop = AndroidUtilities.dp(20);
    private static final float translateBot = AndroidUtilities.dp(6);
    private static final float padding = AndroidUtilities.dp(10);
    private static final float inflate = AndroidUtilities.dp(16);
    private static final float iconSize = AndroidUtilities.dp(32);
    private static final float offset = AndroidUtilities.dp(8);

    private static final int maxUserCellsCount = 5;

    private static boolean useBlur = true;

    private static final int multDuration = 2;
    private static final int startDuration = multDuration * 120;
    private static final int centerDuration = multDuration * 120;
    private static final int endDuration = multDuration * 50;
    private static final int selectCellDuration = multDuration * 120;
    private static final int hideDuration = multDuration * 120;
    private static final int startSendDuration = multDuration * 120;
    private static final int centerSendDuration = multDuration * 150;
    private static final int delaySendDuration = multDuration * 500;
    private static final int endSendDuration = multDuration * 500;
    private static int cellUnselectedAlpha = 150;
    private static int cellSelectedAlpha = 255;
    private static final Drawable iconDrawable = Theme.getThemeDrawable(Theme.key_drawable_shareIcon);
    private static final Drawable goIconDrawable = Theme.getThemeDrawable(Theme.key_drawable_goIcon);
    private static final String FwdMessageToUser = AndroidUtilities.replaceTags(LocaleController.formatString("FwdMessageToUser", R.string.FwdMessageToUser, "")).toString().replace(".", "");

    public static boolean isShow(ChatMessageCell cell)
    {
        ShareView view = instance;
        if(view != null)
        {
            Control control = view.controls.get(cell);
            return control != null && control.animationRunnung;
        }
        return false;
    }

    public static void show(ChatMessageCell cell)
    {
        if (instance == null)
            instance = new ShareView(cell);
        else
            instance.addControl(cell);
    }

    public static void hide()
    {
        ShareView view = instance;
        if (view != null)
        {
            instance = null;
            view.container.removeView(view);
        }
    }

    private static void hide(ChatMessageCell cell)
    {
        ShareView view = instance;
        if (view != null)
            view.removeControl(cell);
    }

    public static void drawInstance(Canvas canvas)
    {
        ShareView view = instance;
        if (view != null)
        {
            canvas.save();
            canvas.translate(view.getX(), view.getY());
            view.draw(canvas);
            canvas.restore();
        }
    }

    private static void roundRect(Path path, RectF bounds)
    {
        path.reset();
        float x = bounds.left, y = bounds.top, w = bounds.width(), h = bounds.height(), r = bounds.height() / 2;

        float startX = x + r;
        float startY = y + h;

        float endX = x + w - r;

        path.moveTo(startX, startY);
        path.quadTo(x, y + h, x, y + h - r);
        path.quadTo(x, y, x + r, y);

        path.lineTo(x + w - r, y);
        path.quadTo(x + w, y, x + w, y + r);
        path.quadTo(x + w, y + h, endX, startY);

        path.lineTo(startX, startY);

        path.close();
    }

    private static float getOffset(float radius, float p, float p0)
    {
        float offset = (float) Math.sqrt(Math.round(radius * radius) - Math.round(Math.pow(p - p0, 2)));
        if (Float.isNaN(offset))
            return 0;
        return offset;
    }

    private static boolean tail(Path path, RectF bounds, RectF tail)
    {
        path.reset();
        float dh = tail.bottom - bounds.bottom;
        if (dh > 0)
        {
            float x = bounds.left, y = bounds.top, r = bounds.right, b = bounds.bottom, rad = bounds.height() / 2;
            float tx = tail.left, ty = tail.top, tr = tail.right, tb = tail.bottom, trad = tail.height() / 2;

            float startX = Math.max(tx - trad, x);
            float startY = b;
            if (startX < x + rad)
                startY = y + rad + getOffset(rad, startX, x + rad);

            float endX = Math.min(tr + trad, r);
            float endY = b;
            if (endX > r - rad)
                endY = b - rad + getOffset(rad, endX, r - rad);

            float tcx = (tx + tr) / 2;

            path.moveTo(startX, startY);

            float py = tb - dh / 2;
            float offsetX = getOffset(trad, py, tb - trad);
            float px = tcx - offsetX;
            path.quadTo(startX + trad / 6 , py - trad / 6, px, py);
            path.quadTo((tcx + px) / 2, tb, tcx, tb);

            px = tcx + offsetX;
            path.quadTo((tcx + px) / 2 , tb, px, py);
            path.quadTo(endX, py - trad / 6, endX, endY);

            path.lineTo(startX, startY);
            path.close();

            return true;
        }
        return false;
    }

    private static float getValue(float start, float end, float f)
    {
        return start * (1 - f) + end * f;
    }

    private static int getColor(int start, int end, float f)
    {
        f = Math.max(Math.min(f, 1), 0);
        float f2 = 1.0f - f;
        return Color.argb((int) (Color.alpha(end) * f + Color.alpha(start) * f2),
                (int) (Color.red(end) * f + Color.red(start) * f2),
                (int) (Color.green(end) * f + Color.green(start) * f2),
                (int) (Color.blue(end) * f + Color.blue(start) * f2));
    }

    private static int setAlpha(int alpha, int color)
    {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int alphaCombine(float totalAlpha, float alpha)
    {
        return (int) (alpha * totalAlpha / 255f);
    }


    public ShareView(ChatMessageCell cell)
    {
        super(cell.getContext());
        this.container = (ViewGroup) cell.getParent().getParent();

        this.backPaint.setStyle(Paint.Style.FILL);
        this.gradientPaint.setStyle(Paint.Style.FILL);
//        this.gradientPaint.setMaskFilter(new BlurMaskFilter(dp(4), BlurMaskFilter.Blur.NORMAL));
        this.borderPaint.setStyle(Paint.Style.STROKE);
        this.arrowDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_background), PorterDuff.Mode.MULTIPLY));
        this.checkedDrawable.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_undo_background), PorterDuff.Mode.MULTIPLY));
        Control control = new Control(cell);
        this.controls.put(cell, control);
        this.visibleControls.add(control);
        this.container.addView(this);

        this.listView = (RecyclerListView)cell.getParent();
        if(this.listView != null)
        {
            this.listView.addOnLayoutChangeListener(this.layoutChangeListener);
            this.listView.addOnScrollListener(this.scrollListener);
        }
    }

    @Override
    protected void onDetachedFromWindow()
    {
        this.listView.removeOnScrollListener(this.scrollListener);
        this.listView.removeOnLayoutChangeListener(this.layoutChangeListener);
        super.onDetachedFromWindow();
    }


    private final ViewGroup container;
    private final RecyclerListView listView;

    private final HashMap<ChatMessageCell, Control> controls = new HashMap<>();
    private final ArrayList<Control> visibleControls = new ArrayList<>();
    private final Paint backPaint = new Paint();
    private final Paint borderPaint = new Paint();
    private final Paint gradientPaint = new Paint();

    private final RectF mainBounds = new RectF();
    private final RectF tempRect = new RectF();
    private final int currentAccount = UserConfig.selectedAccount;
    private final ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    private final Drawable arrowDrawable = Theme.chat_botInlineDrawable;
    private final Drawable checkedDrawable = getContext().getResources().getDrawable(R.drawable.background_selected).mutate();
    private final int dialogBackgroundGray = Theme.getColor(Theme.key_dialogBackgroundGray);
    private final Paint chatActionBackground = Theme.getThemePaint(Theme.key_paint_chatActionBackground);
    private final int chatActionBackgroundColor = setAlpha(120, chatActionBackground.getColor());
    private final int dialogBackground = Theme.getColor(Theme.key_dialogBackground);
    private final TextPaint chatActionText = (TextPaint) Theme.getThemePaint(Theme.key_paint_chatActionText);
    private final int undo_background = Theme.getColor(Theme.key_undo_background);
    private final int undo_infoColor = Theme.getColor(Theme.key_undo_infoColor);
    private final int undo_cancelColor = Theme.getColor(Theme.key_undo_cancelColor);

    private final OnLayoutChangeListener layoutChangeListener = new OnLayoutChangeListener()
    {
        public void onLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft, int oldTop, int oldRight, int oldBottom)
        {
            mainBounds.set(left, top, right, bottom);
            for (Control control : visibleControls)
                if(!control.hideEnded)
                    control.getSideBounds();
            container.invalidate();

        }
    };

    private final RecyclerView.OnScrollListener scrollListener = new RecyclerView.OnScrollListener()
    {
        @Override
        public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy)
        {
            for (Control control : visibleControls)
                if(!control.hideEnded)
                    control.getSideBounds();
            container.invalidate();
        }
    };


    private void addControl(ChatMessageCell cell)
    {
        for (Control control : this.controls.values())
            control.hideUndo();

        if (!controls.containsKey(cell))
        {
            Control control = new Control(cell);
            this.controls.put(cell, control);
            this.visibleControls.add(control);
            control.startShowFrame();
        }
    }

    private void removeControl(ChatMessageCell cell)
    {
        this.controls.remove(cell);
        for (Control ct : this.visibleControls)
        {
            if(ct.cell == cell)
            {
                this.visibleControls.remove(ct);
                break;
            }
        }
        if(this.visibleControls.size() == 0)
            hide();
    }

    private Control first()
    {
        return this.visibleControls.get(0);
    }

    private void offsetPosition(ChatMessageCell cell, RectF rect)
    {
        rect.offset(-getLeft(), -getTop());
        ViewGroup current = cell;
        while (current != null && current != container)
        {
            rect.offset(current.getLeft(), current.getTop());
            current = (ViewGroup) current.getParent();
        }
    }

    private boolean testSelctedSell(float x, float y)
    {
        for (Control control : this.visibleControls)
        {
            if (!control.hideStarted && control.undoView == null)
            {
//                if(!control.animationRunnung)
                {
                    Control.UserCell selecteCell = null;
                    for (Control.UserCell cell : control.userCells)
                        if (cell.cellBounds.contains(x, y))
                        {
                            selecteCell = cell;
                            break;
                        }
                    control.setSelectedCell(selecteCell);
                }
                return control.controlBounds.contains(x, y);
            }
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e)
    {
        switch (e.getAction())
        {
            case MotionEvent.ACTION_DOWN:
                boolean useDown = this.testSelctedSell(e.getX(), e.getY());
                if(!useDown)
                {
                    for (Control control : this.visibleControls)
                    {
                        if (control.undoView != null)
                        {
                            if (!control.hideUndoStarted && control.undoView.undoBounds.contains(e.getX(), e.getY()))
                            {
                                if (control.undoView.linkBounds.contains(e.getX(), e.getY()))
                                {
                                    BaseFragment lastFragment = LaunchActivity.getLastFragment();
                                    if (lastFragment != null)
                                    {
                                        Bundle args = new Bundle();
                                        if (control.undoView.cell.tlObject instanceof TLRPC.User)
                                            args.putLong("user_id", ((TLRPC.User) control.undoView.cell.tlObject).id);
                                        else
                                            args.putLong("user_id", ((TLRPC.Chat) control.undoView.cell.tlObject).id);
                                        lastFragment.presentFragment(new ChatActivity(args));
                                        useDown = true;
                                        hide();
                                    }
                                }
                            }
                            else
                            {
                                control.hideUndo();
                            }
                        }
                        else
                        {
                            control.hideAnimation(() -> hide(control.cell));
                        }
                    }
                }
                if(!useDown)
                {
                    ViewGroup parent = (ViewGroup) this.first().cell.getParent();
                    for (int i = 0; i < parent.getChildCount(); i++)
                    {
                        View child = parent.getChildAt(i);
                        if (child instanceof ChatMessageCell)
                        {
                            ChatMessageCell cell = (ChatMessageCell) child;
                            if (cell.getTop() < e.getY() && cell.getBottom() > e.getY())
                            {
                                cell.getSideBounds(tempRect);
                                offsetPosition(cell, tempRect);
                                if (tempRect.contains(e.getX(), e.getY()))
                                    return false;
                            }
                        }
                    }
                }
                return useDown;
            case MotionEvent.ACTION_UP:
                for (Control control : this.visibleControls)
                {
                    if (!control.hideStarted && control.selectedCell != null && control.controlBounds.contains(e.getX(), e.getY()))
                    {
                        control.startSend();
                        break;
                    }
                }
                break;
            case MotionEvent.ACTION_MOVE:
                return this.testSelctedSell(e.getX(), e.getY());
            default:
                break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b)
    {
        this.mainBounds.set(l, t, r, b);

        if(this.dialogs.size() == 0)
        {
            long selfUserId = UserConfig.getInstance(currentAccount).clientUserId;
            if (!MessagesController.getInstance(currentAccount).dialogsForward.isEmpty())
            {
                TLRPC.Dialog dialog = MessagesController.getInstance(currentAccount).dialogsForward.get(0);
                this.dialogs.add(dialog);
            }

            ArrayList<TLRPC.Dialog> ds = MessagesController.getInstance(currentAccount).getAllDialogs();
            for (int i = 0; i < ds.size(); i++)
            {
                TLRPC.Dialog dialog = ds.get(i);
                if (!(dialog instanceof TLRPC.TL_dialog) || dialog.id == selfUserId)
                    continue;
                if (!DialogObject.isEncryptedDialog(dialog.id))
                {
                    if (DialogObject.isUserDialog(dialog.id))
                        this.dialogs.add(dialog);
                    else
                    {
                        TLRPC.Chat chat = MessagesController.getInstance(currentAccount).getChat(-dialog.id);
                        if (!(chat == null || ChatObject.isNotInChat(chat) || chat.gigagroup && !ChatObject.hasAdminRights(chat) || ChatObject.isChannel(chat) && !chat.creator && (chat.admin_rights == null || !chat.admin_rights.post_messages) && !chat.megagroup))
                            this.dialogs.add(dialog);
                    }
                }
                if (this.dialogs.size() >= maxUserCellsCount)
                    break;
            }

            if (this.dialogs.size() == 0)
            {
                hide();
                return;
            }
        }
        for (Control control : this.controls.values())
            control.startShowFrame();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
    {
        ViewGroup parent = (ViewGroup) first().cell.getParent();
        if(parent != null)
            this.setMeasuredDimension(parent.getWidth(), parent.getHeight());
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas)
    {
        canvas.save();
        canvas.clipRect(0, 0, this.getWidth(), this.getHeight());
        this.borderPaint.setColor(dialogBackgroundGray);
        this.borderPaint.setStrokeWidth(1);

        int oldAlpha = backPaint.getAlpha();
        for (Control control : this.visibleControls)
            control.draw(canvas);
        this.backPaint.setAlpha(oldAlpha);

        canvas.restore();

        canvas.save();
        canvas.clipRect(0, 0, this.getWidth(), this.getHeight() + AndroidUtilities.dp(100));
        for (Control control : this.visibleControls)
            if(control.undoView != null)
                control.undoView.draw(canvas);
        canvas.restore();
    }

    public class Control
    {
        private Control(ChatMessageCell cell)
        {
            this.cell = cell;
            this.messageObject = this.cell.getMessageObject();
        }

        private final ChatMessageCell cell;
        private final MessageObject messageObject;
        private final Path boundsPath = new Path();
        private final Path tailPath = new Path();
        private final Path gradientPath = new Path();
//        private final Path sidePath = new Path();
        private final RectF sideRect = new RectF();
        private final RectF sideBounds = new RectF();
        private final RectF startBounds = new RectF();
        private final RectF centerBounds = new RectF();
        private final RectF endBounds = new RectF();
        private final RectF controlBounds = new RectF();

        private boolean hideStarted = false;
        private boolean hideEnded = false;
        private float cellSize = AndroidUtilities.dp(64);
        private int cellsLevelCount;

        private final ArrayList<UserCell> userCells = new ArrayList<>();
        private UserCell selectedCell;
        private UndoView undoView;

        private final RectAnimationInfo boundsInfo = new RectAnimationInfo();
        private final RectAnimationInfo tailInfo = new RectAnimationInfo();
        private final FloatAnimationInfo rotationInfo = new FloatAnimationInfo();
        private final FloatAnimationInfo translationInfo = new FloatAnimationInfo();
        private final ColorAnimationInfo backColorInfo = new ColorAnimationInfo();
        private final IntegerAnimationInfo currentAlpha = new IntegerAnimationInfo(255);
        private final FloatAnimationInfo cellOffsetX = new FloatAnimationInfo(AndroidUtilities.dp(64));

        private boolean animationRunnung;
        private boolean centerSendStarted = false;
        private boolean showFrameStarted = false;
        private boolean hideUndoStarted = false;

        public void draw(Canvas canvas)
        {
            if (this.hideEnded)
                return;

            this.controlBounds.set(this.boundsInfo.current);
            this.controlBounds.offset(0, this.sideBounds.top);
            this.tailInfo.current.offset(0, this.sideBounds.top);
            ;
            this.sideRect.set(sideBounds);
            this.sideRect.offset(0, translationInfo.current);

            boolean sideVisible = (!this.hideStarted || this.animationRunnung) && !this.cell.getSideButtonVisible();

            if (sideVisible)
            {
                backPaint.setColor(chatActionBackgroundColor);
                canvas.drawRoundRect(this.sideRect, this.sideRect.height() / 2, this.sideRect.height() / 2, backPaint);
            }

            borderPaint.setAlpha(this.currentAlpha.current);
            backPaint.setColor(this.backColorInfo.current);
            backPaint.setAlpha(this.currentAlpha.current);

            roundRect(boundsPath, this.controlBounds);
            tail(tailPath, this.controlBounds, this.tailInfo.current);
            boundsPath.op(tailPath, Path.Op.UNION);

            gradientPath.reset();
            gradientPath.addOval(sideRect, Path.Direction.CW);
            gradientPath.op(boundsPath, Path.Op.INTERSECT);

            canvas.save();
            canvas.clipPath(boundsPath);
            canvas.clipPath(gradientPath, Region.Op.DIFFERENCE);
            canvas.drawPath(boundsPath, backPaint);
            canvas.drawPath(boundsPath, borderPaint);
            canvas.restore();

            int[] colors = new int[2];
            colors[0] = setAlpha(0, this.backColorInfo.start);
            colors[1] = this.backColorInfo.current;

            float[] stops = new float[2];
            stops[0] = 0.8f;
            stops[1] = 1.0f;

            float radius = Math.max(1, sideRect.height() * 2 - (sideRect.bottom - controlBounds.bottom) * 1.5f);
            float cx = (sideRect.left + sideRect.right) / 2;
            float cy = sideRect.top + radius;

            RadialGradient gradient = new RadialGradient(cx, cy, radius, colors, stops, Shader.TileMode.CLAMP);

            gradientPaint.setShader(gradient);
            gradientPaint.setAlpha(this.currentAlpha.current);
            canvas.drawPath(gradientPath, gradientPaint);

            if (sideVisible)
            {
                final int scx = (int) (sideRect.left + iconSize / 2), scy = (int) (sideRect.top + iconSize / 2);
                final int shw = iconDrawable.getIntrinsicWidth() / 2, shh = iconDrawable.getIntrinsicHeight() / 2;

                canvas.save();
                canvas.rotate(this.rotationInfo.current, scx, scy);

                Drawable drawable = iconDrawable;
                if (this.cell.getDrawSideButton() == 2)
                    drawable = goIconDrawable;
                drawable.setBounds(scx - shw, scy - shh, scx + shw, scy + shh);
                drawable.draw(canvas);

                canvas.restore();
            }

            for (UserCell cell : userCells)
                cell.draw(canvas);

            this.tailInfo.current.offset(0, -this.sideBounds.top);
        }

        private void getSideBounds()
        {
            this.cell.getSideBounds(this.sideBounds);
            offsetPosition(this.cell, this.sideBounds);
        }

        private void setSelectedCell(UserCell value)
        {
            if (this.selectedCell == value) return;
            final UserCell prev = this.selectedCell;
            this.selectedCell = value;
            final UserCell current = this.selectedCell;

            if (current != null)
            {
                current.sizeInfo.set(current.sizeInfo.current, cellSize + offset);
                current.alpha.set(current.alpha.current, cellSelectedAlpha);
                current.hintAlpha.set(current.hintAlpha.current, 230);
            }
            if (prev != null)
            {
                prev.sizeInfo.set(prev.sizeInfo.current, cellSize);
                prev.hintAlpha.set(prev.hintAlpha.current, 0);
            }
            for (UserCell cell : userCells)
                if (cell != current)
                    cell.alpha.set(cell.alpha.current, current != null ? cellUnselectedAlpha : cellSelectedAlpha);

            ValueAnimator animator = ValueAnimator.ofFloat(0, 1);
            animator.setInterpolator(endShowInterpolator);
            animator.setDuration(selectCellDuration);

            animator.addUpdateListener((animation) ->
            {
                float v = (Float) animation.getAnimatedValue();
                if (current != null)
                {
                    current.sizeInfo.update2(v);
                    current.hintAlpha.update(v);
                    if (prev != null)
                        current.alpha.update(v);
                }
                if (prev != null)
                {
                    prev.sizeInfo.update2((Float) v);
                    prev.hintAlpha.update(v);
                }
                for (UserCell cell : userCells)
                    if (cell != current)
                        cell.alpha.update(v);
                container.invalidate();
            });
            animator.start();
        }

        private void invalidateCell()
        {
            ViewParent parent = cell.getParent();
            if (parent != null)
                ((ViewGroup)parent).invalidate();
        }

        private void startShowFrame()
        {
            this.getSideBounds();
            if(!this.showFrameStarted)
            {
                this.showFrameStarted = true;
                for (int i = 0; i < dialogs.size(); i++)
                    this.userCells.add(new UserCell(dialogs.get(i), i));


                this.cellSize = Math.min(AndroidUtilities.dp(64), (mainBounds.width() - offset * 4 - rightOffset * 2 - padding * 2 - (this.userCells.size() - 1) * inflate) / this.userCells.size());
                float right = mainBounds.right - rightOffset - offset;
                float width = this.userCells.size() * this.cellSize + (this.userCells.size() - 1) * inflate + padding * 2;
                float left = right - width;
                float height = this.cellSize + padding * 2;
                float top = - height - translateTop;
                float bottom = top + height;

                int totalDuration = startDuration + centerDuration;
                this.cellsLevelCount = (int) Math.ceil(userCells.size() / 2.0);
                int delay = Math.round(totalDuration / ((float) this.cellsLevelCount + 0.0f));

                this.startBounds.set(this.sideBounds.left, 0, this.sideBounds.right, this.sideBounds.height());
                this.centerBounds.set(left - offset, top - offset, right + offset, bottom + offset);
                this.endBounds.set(left, top, right, bottom);

                Frame frame = new Frame();
                frame.started = () ->
                {
                    animationRunnung = true;
                    invalidateCell();
                };
                frame.ended = () -> endShowFrame();

                frame.add(this.rotationInfo.create(0f, -45f, linearInterpolator, startDuration));
                frame.add(this.rotationInfo.create(-45f, 45f, linearInterpolator, centerDuration, startDuration));
                frame.add(this.translationInfo.create(0f, -translateTop, linearInterpolator, startDuration));
                frame.add(this.translationInfo.create(-translateTop, translateBot, linearInterpolator, centerDuration, startDuration));
                frame.add(this.cellOffsetX.create(cellOffsetX.current, 0f, cellOffsetXInterpolator, totalDuration));

                frame.add(this.backColorInfo.create(chatActionBackgroundColor, dialogBackground, backColorInterpolator, startDuration));

                for (UserCell cell : userCells)
                {
                    int level = cell.index + 1;
                    if (level > this.cellsLevelCount)
                        level = userCells.size() - level + 1;
                    level = this.cellsLevelCount - level + 1;
                    cell.level = level;

                    frame.add(cell.sizeInfo.create(0f, cellSize + offset, linearInterpolator, totalDuration - level * delay, (level * delay), (value) ->
                    {
                        cell.sizeInfo.update(value);
                        container.invalidate();
                    }));
                }
                this.tailInfo.end.set(this.startBounds.left, this.endBounds.bottom - this.startBounds.height(), this.startBounds.right, this.endBounds.bottom);
                frame.add(this.tailInfo.create(this.startBounds, this.tailInfo.end, linearInterpolator, totalDuration));

                frame.add(this.boundsInfo.create(this.startBounds, this.centerBounds, horBoundsInterpolator, totalDuration, 0, (value) ->
                {
                    this.boundsInfo.updateHor(value);
                }));

                frame.add(this.boundsInfo.create(this.startBounds, this.centerBounds, verBoundsInterpolator, totalDuration, 0, (value) ->
                {
                    this.boundsInfo.updateVert(value);
                    container.invalidate();
                }));

                frame.start();
            }
        }

        private void endShowFrame()
        {
            Frame frame = new Frame(endShowInterpolator, endDuration);
            frame.ended = () ->
            {
                if(animationRunnung)
                {
                    animationRunnung = false;
                    invalidateCell();
                }
            };
            frame.add(this.rotationInfo.create(45f, 0f));
            frame.add(this.translationInfo.create(translateBot, 0f));
            for (UserCell cell : userCells)
            {
                frame.add(cell.sizeInfo.create(cellSize + offset, cellSize, null, endDuration / 2, 0, (value) ->
                {
                    cell.sizeInfo.update(value);
                    container.invalidate();
                }));
            }
            frame.add(this.boundsInfo.create(this.centerBounds, this.endBounds));
            frame.start();
        }

        private void hideAnimation(Runnable ended)
        {
            if (!this.hideStarted)
            {
                this.hideStarted = true;
                ValueAnimator animator = currentAlpha.create((Integer) 255, (Integer) 0, endShowInterpolator, hideDuration, 0, (value) ->
                {
                    currentAlpha.update(value);
                    container.invalidate();
                });
                animator.addListener(new AnimatorListenerAdapter()
                {
                    @Override
                    public void onAnimationEnd(Animator animation)
                    {
                        if(animationRunnung)
                        {
                            postDelayed(()->
                            {
                                if(animationRunnung)
                                {
                                    animationRunnung = false;
                                    controls.remove(cell);
                                    invalidateCell();
                                }
                                hideEnded = true;
                                if (ended != null)
                                    ended.run();
                            }, centerDuration);
                        }
                        else
                        {
                            hideEnded = true;
                            controls.remove(cell);
                            if (ended != null)
                                ended.run();
                        }
                    }
                });
                animator.start();
                if(!animationRunnung)
                    controls.remove(this.cell);
            }
        }


        private void startSend()
        {
            UserCell userCell = this.selectedCell;
            if (userCell != null && this.undoView == null)
            {
                this.undoView = new UndoView(userCell);
                ArrayList<MessageObject> messages = new ArrayList<>();
                messages.add(this.cell.getMessageObject());
                int result = SendMessagesHelper.getInstance(currentAccount).sendMessage(messages, userCell.dialog.id, true,false, false, 0, null);

                this.hideAnimation(null);

                float top = 0;
                float offsetY = this.undoView.getHeight() + AndroidUtilities.dp(10);
                this.undoView.undoBoundsInfo.start.set(offset, top, mainBounds.width() - offset, top + this.undoView.getHeight());
                this.undoView.undoBoundsInfo.end.set(this.undoView.undoBoundsInfo.start.left, this.undoView.undoBoundsInfo.start.top - offsetY, this.undoView.undoBoundsInfo.start.right, this.undoView.undoBoundsInfo.start.bottom - offsetY);

                float imageSize = AndroidUtilities.dp(28);
                float left = this.undoView.undoBoundsInfo.end.left + this.undoView.padding;

                Frame frame = new Frame();

                frame.add(this.undoView.undoBoundsInfo.create(this.undoView.undoBoundsInfo.start, this.undoView.undoBoundsInfo.end, linearInterpolator, startSendDuration));
                frame.add(this.undoView.startAlpha.create((Integer) 0, (Integer) UndoView.maxAlpha, linearInterpolator, startSendDuration));

                frame.add(this.undoView.imageSize.create(userCell.cellBounds.width(), imageSize, linearInterpolator, startSendDuration));
                frame.add(this.undoView.imageLeft.create(userCell.cellBounds.left, left, xImageInterpolator, startSendDuration));
                frame.add(this.undoView.imageTop.create(userCell.cellBounds.top - mainBounds.height(), (this.undoView.undoBoundsInfo.end.height() - imageSize) / 2, yImageInterpolator, startSendDuration, (value) ->
                {
                    this.undoView.imageTop.update(value);
                    if (this.undoView.imageTop.current > this.undoView.undoBoundsInfo.current.top)
                        this.centerSend();
                    container.invalidate();
                }));
                frame.start();

                userCell.visibleImage = false;
            }
        }

        private void centerSend()
        {
            if (!this.centerSendStarted)
            {
                this.centerSendStarted = true;
                int duration = centerSendDuration + centerSendDuration / 4;

                Frame frame = new Frame(linearInterpolator, duration);
                frame.ended = () -> this.centerSend2();
                frame.add(this.undoView.imageAlpha.create((Integer) 255, (Integer) 0, centerSendDuration / 3, (value) ->
                {
                    this.undoView.imageAlpha.update(value);
                    container.invalidate();
                }));

                frame.add(this.undoView.iconRotation.create(-90f, 0f, centerSendDuration, duration - centerSendDuration));

                float imageSize = this.undoView.imageSize.current;
                float left = this.undoView.padding;
                float top = (this.undoView.undoBoundsInfo.end.height() - imageSize) / 2;
                this.undoView.circleBounds.end.set(left, top, left + imageSize, top + imageSize);
                left = this.undoView.circleBounds.end.left + this.undoView.circleBounds.end.width() / 2;
                top = this.undoView.circleBounds.end.top + this.undoView.circleBounds.end.height() / 2;
                this.undoView.circleBounds.start.set(left, top, left, top);
                frame.add(this.undoView.circleBounds.create(this.undoView.circleBounds.start, this.undoView.circleBounds.end, centerSendDuration, duration - centerSendDuration, (value) ->
                {
                    this.undoView.circleBounds.update(value);
                    container.invalidate();
                }));
                frame.start();
            }
        }

        private void centerSend2()
        {
            Frame frame = new Frame(linearInterpolator, centerSendDuration);
            frame.ended = () -> this.centerSend3();

            frame.add(this.undoView.iconRotation.create(0f, 165f));

            RectF rect = this.undoView.circleBounds.current;
            float padding = AndroidUtilities.dp(4);
            float offsetX = AndroidUtilities.dp(4);
            float offsetY = AndroidUtilities.dp(0);
            this.undoView.circleBounds.end.set(rect.left + padding + offsetX, rect.top + padding + offsetY, rect.right - padding + offsetX, rect.bottom - padding + offsetY);

            frame.add(this.undoView.circleBounds.create(this.undoView.circleBounds.current, this.undoView.circleBounds.end, (value) ->
            {
                this.undoView.circleBounds.update(value);
                container.invalidate();
            }));
            frame.start();
        }

        private void centerSend3()
        {
            Frame frame = new Frame(linearInterpolator, centerSendDuration + centerSendDuration / 3);
            frame.ended = () -> this.centerSend4();

            frame.add(this.undoView.checkedScale.create(0f, 1f, centerSendDuration / 2));


            this.undoView.outCrcleBounds.end.set(this.undoView.circleBounds.start);
            this.undoView.outCrcleBounds.end.inset(-AndroidUtilities.dp(6), -AndroidUtilities.dp(6));
            frame.add(this.undoView.outCrcleBounds.create(this.undoView.circleBounds.current, this.undoView.outCrcleBounds.end));
            frame.add(this.undoView.outCrcleAlpha.create((Integer) 255, (Integer) 0));

            frame.add(this.undoView.iconRotation.create(165f, 210f, centerSendDuration / 2));
            frame.add(this.undoView.iconRotation.create(210f, 150f, centerSendDuration / 2, centerSendDuration / 2, (value) ->
            {
                this.undoView.iconRotation.update(value);
                container.invalidate();
            }));

            this.undoView.circleBounds.end.set(this.undoView.circleBounds.start);
            frame.add(this.undoView.circleBounds.create(this.undoView.circleBounds.current, this.undoView.circleBounds.end, (value) ->
            {
                this.undoView.circleBounds.update(value);
                container.invalidate();
            }));

            frame.start();
        }

        private void centerSend4()
        {
            Frame frame = new Frame(linearInterpolator, centerSendDuration / 3);
            frame.ended = () -> postDelayed(() -> this.hideUndo(), delaySendDuration);
            frame.add(this.undoView.iconRotation.create(150f, 165f, (value) ->
            {
                this.undoView.iconRotation.update(value);
                container.invalidate();
            }));
            frame.start();

        }

        private void hideUndo()
        {
            if (!this.hideUndoStarted && this.undoView != null)
            {
                this.hideUndoStarted = true;
                Frame frame = new Frame(linearInterpolator, endSendDuration);
                frame.ended = () -> hide(this.cell);
                frame.add(this.undoView.undoBoundsInfo.create(this.undoView.undoBoundsInfo.current, this.undoView.undoBoundsInfo.start));
                frame.add(this.undoView.endAlpha.create((Integer) 255, (Integer) 0, (value) ->
                {
                    this.undoView.endAlpha.update(value);
                    container.invalidate();
                }));
                frame.start();
            }
        }

        private class UserCell
        {
            public UserCell(TLRPC.Dialog dialog, int index)
            {
                this.dialog = dialog;
                this.index = index;
                this.tlObject = MessagesController.getInstance(currentAccount).getUserOrChat(this.dialog.id);
                this.avatarDrawable.setInfo(currentAccount, this.tlObject);
                if (this.tlObject != null)
                {
                    if (this.tlObject instanceof TLRPC.Chat)
                    {
                        TLRPC.Chat chat = (TLRPC.Chat) this.tlObject;
                        this.name = chat.title;
                        if (chat.color != null)
                            this.hintBackColor = AvatarDrawable.getColorForId(chat.color.color);
                    }
                    else
                    {
                        TLRPC.User user = (TLRPC.User) this.tlObject;
                        if (UserObject.isReplyUser(user))
                        {
                            this.name = LocaleController.getString(R.string.RepliesTitle);
                            this.avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_REPLIES);
                        }
                        else if (UserObject.isUserSelf(user))
                        {
                            this.name = LocaleController.getString(R.string.SavedMessages);
                            this.avatarDrawable.setAvatarType(AvatarDrawable.AVATAR_TYPE_SAVED);
                        }
                        else
                            this.name = ContactsController.formatName(user.first_name, user.last_name);
                        if (user.color != null)
                            this.hintBackColor = AvatarDrawable.getColorForId(user.color.color);

                    }
                }
                this.imageReceiver.setForUserOrChat(this.tlObject, this.avatarDrawable);
                this.imageReceiver.setRoundRadius((int) ((cellSize + offset) / 2));
                if (this.hintBackColor == 0)
                    this.hintBackColor = chatActionBackgroundColor;
            }

            private final TLRPC.Dialog dialog;
            private TLObject tlObject;
            private String name;
            private final int index;
            private int level;
            private StaticLayout textLayout;
            private final TextPaint textPaint = new TextPaint();
            private int hintBackColor;
            private boolean visibleImage = true;
            private final RectF cellBounds = new RectF();
            private final RectF cellHintBounds = new RectF();
            private final int hintOffsetY = AndroidUtilities.dp(40);
            private final int hintPadding = AndroidUtilities.dp(8);
            private float hintHeight;
            private final FloatAnimationInfo sizeInfo = new FloatAnimationInfo();
            private final IntegerAnimationInfo alpha = new IntegerAnimationInfo(255);
            private final IntegerAnimationInfo hintAlpha = new IntegerAnimationInfo(0);

            private final ImageReceiver imageReceiver = new ImageReceiver();
            private final AvatarDrawable avatarDrawable = new AvatarDrawable();

            public void calcHintBounds()
            {
                if (this.textLayout == null)
                {
                    this.textPaint.set(chatActionText);
                    this.textLayout = new StaticLayout(this.name, this.textPaint, (int) cellSize * 2, Layout.Alignment.ALIGN_CENTER, 1.0f, 0.0f, false);
                    final int count = this.textLayout.getLineCount();
                    float width = 0;
                    float height = hintPadding * 2;
                    for (int i = 0; i < count; i++)
                    {
                        width = Math.max(width, this.textLayout.getLineWidth(i));
                        height += this.textLayout.getLineBottom(i) - this.textLayout.getLineTop(i);
                        if (i < count - 1)
                            height += AndroidUtilities.dp(3);
                    }
                    width += hintPadding * 2;
                    float left = Math.max(Math.min(this.cellBounds.left - (width - this.cellBounds.width()) / 2, container.getRight() - width - offset), container.getLeft() + offset);
                    hintHeight = height;
                    this.cellHintBounds.set(left, 0, left + width, 0);
                }
                this.cellHintBounds.top = this.cellBounds.top - hintOffsetY - hintHeight;
                this.cellHintBounds.bottom = this.cellHintBounds.top + hintHeight;
            }

            private void updateBounds()
            {
                float size = sizeInfo.current;

                float offsetX = cellOffsetX.current * (this.level - 1);
                if(this.index >= cellsLevelCount)
                    offsetX = -offsetX;

                float x = offsetX + controlBounds.left - (endBounds.width() - controlBounds.width()) / 2 + cellSize / 2 + padding + this.index * (cellSize + inflate) - size / 2;
                float y = controlBounds.top - (endBounds.height() - controlBounds.height()) / 2 + padding + cellSize / 2 - size / 2;
                this.cellBounds.set(x, y, x + size, y + size);
            }

            public void draw(Canvas canvas)
            {
                float size = sizeInfo.current;
                if (size > 0)
                {
                    updateBounds();
                    if (this.visibleImage)
                    {
                        this.imageReceiver.setImageCoords(this.cellBounds.left, this.cellBounds.top, this.cellBounds.width(), this.cellBounds.height());
                        this.imageReceiver.setAlpha(alphaCombine(currentAlpha.current, alpha.current) / 255f);
                        this.imageReceiver.draw(canvas);
                    }
                    if (this.name != null && this.hintAlpha.current > 0)
                    {
                        if(useBlur && this.hintAlpha.current < 255)
                        {
                            BlurMaskFilter blurMaskFilter = new BlurMaskFilter(dp(10 * (255f - this.hintAlpha.current) / 255f), BlurMaskFilter.Blur.NORMAL);
                            backPaint.setMaskFilter(blurMaskFilter);
                            this.textPaint.setMaskFilter(blurMaskFilter);
                        }

                        this.calcHintBounds();

                        backPaint.setColor(this.hintBackColor);

                        int oldAlpha = backPaint.getAlpha();
                        backPaint.setAlpha(alphaCombine(currentAlpha.current, this.hintAlpha.current));

                        canvas.drawRoundRect(this.cellHintBounds, AndroidUtilities.dp(16), AndroidUtilities.dp(16), backPaint);

                        this.textPaint.setAlpha(backPaint.getAlpha());
                        float tx = this.cellHintBounds.left + hintPadding - (this.textLayout.getWidth() - (this.cellHintBounds.width() - hintPadding * 2)) / 2;
                        float ty = this.cellHintBounds.top + hintPadding;
                        canvas.translate(tx, ty);
                        this.textLayout.draw(canvas);
                        canvas.translate(-tx, -ty);

                        backPaint.setAlpha(oldAlpha);
                        backPaint.setMaskFilter(null);
                    }
                }
            }
        }

        private class UndoView
        {
            public UndoView(UserCell cell)
            {
                this.cell = cell;
                this.text = FwdMessageToUser;
                this.textPaint.set(cell.textPaint);
                this.textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            }

            private final static int maxAlpha = 255;

            private final String text;
            private final UserCell cell;
            private final TextPaint textPaint = new TextPaint();
            private final IntegerAnimationInfo startAlpha = new IntegerAnimationInfo(0);
            private final IntegerAnimationInfo endAlpha = new IntegerAnimationInfo(maxAlpha);
            private final IntegerAnimationInfo outCrcleAlpha = new IntegerAnimationInfo(255);
            private final IntegerAnimationInfo imageAlpha = new IntegerAnimationInfo(255);
            private final RectAnimationInfo undoBoundsInfo = new RectAnimationInfo();
            private final RectAnimationInfo circleBounds = new RectAnimationInfo();
            private final RectAnimationInfo outCrcleBounds = new RectAnimationInfo();
            private final FloatAnimationInfo iconRotation = new FloatAnimationInfo(-90f);
            private final FloatAnimationInfo checkedScale = new FloatAnimationInfo(0f);

            private final FloatAnimationInfo imageLeft = new FloatAnimationInfo();
            private final FloatAnimationInfo imageTop = new FloatAnimationInfo();
            private final FloatAnimationInfo imageSize = new FloatAnimationInfo();

            private StaticLayout textLayout;
            private StaticLayout linkTextLayout;
            private final float padding = AndroidUtilities.dp(10);
            private final RectF linkBounds = new RectF();
            private final RectF undoBounds = new RectF();

            private float getHeight()
            {
                this.cell.calcHintBounds();
                return this.cell.cellHintBounds.height() + this.padding * 2;
            }

            private int getAlpha()
            {
                if (this.endAlpha.current < maxAlpha)
                    return this.endAlpha.current;
                return this.startAlpha.current;
            }
            private void setBackAlpha(int maxAlpha)
            {
                if (this.endAlpha.current < maxAlpha)
                    backPaint.setAlpha(alphaCombine(this.endAlpha.current, maxAlpha));
                else
                    backPaint.setAlpha(alphaCombine(this.startAlpha.current, maxAlpha));
            }

            private void setTextAlpha()
            {
                if (this.endAlpha.current < maxAlpha)
                    this.textPaint.setAlpha(this.endAlpha.current);
                else
                    this.textPaint.setAlpha(this.startAlpha.current);
            }

            public void draw(Canvas canvas)
            {
                this.undoBounds.set(this.undoBoundsInfo.current);
                this.undoBounds.offset(0, mainBounds.height());

                int alphaCurrent = getAlpha();
                BlurMaskFilter blurMaskFilter = null;
                if(useBlur && alphaCurrent < maxAlpha)
                    blurMaskFilter = new BlurMaskFilter(dp(10 * (255f - alphaCurrent) / 255f), BlurMaskFilter.Blur.NORMAL);

                int oldAlpha = backPaint.getAlpha();
                int ba = borderPaint.getAlpha();
                borderPaint.setMaskFilter(blurMaskFilter);
                backPaint.setMaskFilter(blurMaskFilter);
                this.textPaint.setMaskFilter(blurMaskFilter);

                backPaint.setColor(undo_background);
                this.setBackAlpha(Color.alpha(undo_background));

                float r = AndroidUtilities.dp(10);
                canvas.drawRoundRect(this.undoBounds, r, r, backPaint);
                if (this.imageAlpha.current > 0)
                {
                    this.cell.imageReceiver.setImageCoords(imageLeft.current, this.undoBounds.top + imageTop.current, imageSize.current, imageSize.current);
                    this.cell.imageReceiver.setAlpha(alphaCombine(endAlpha.current, imageAlpha.current) / 255f);
                    this.cell.imageReceiver.draw(canvas);
                }
                this.textPaint.setColor(undo_infoColor);
                this.setTextAlpha();
                if (this.circleBounds.current.width() > 0)
                {
                    this.circleBounds.current.offset(this.undoBounds.left, this.undoBounds.top);

                    backPaint.setColor(this.textPaint.getColor());
                    this.setBackAlpha(255);
                    r = this.circleBounds.current.height() / 2;
                    canvas.drawRoundRect(this.circleBounds.current, r, r, backPaint);

                    float rx = (this.circleBounds.current.left + this.circleBounds.current.right) / 2;
                    float ry = (this.circleBounds.current.top + this.circleBounds.current.bottom) / 2;
                    canvas.save();
                    canvas.rotate(this.iconRotation.current, rx, ry);

                    int size = (int) (circleBounds.current.width() / 2);

                    float alpha = Math.max(0, 255 * (1 - this.checkedScale.current * 3));
                    if (alpha > 0)
                    {
                        int arrowSize = Math.max(0, (int) (size * (1 - this.checkedScale.current)));
                        int scx = (int) (circleBounds.current.left + (circleBounds.current.width() - size) / 2);
                        int scy = (int) (circleBounds.current.top + size / 6f);
                        arrowDrawable.setAlpha(alphaCombine(this.endAlpha.current, alpha));
                        arrowDrawable.setBounds(scx, scy, scx + arrowSize, scy + arrowSize);
                        arrowDrawable.draw(canvas);
                    }
                    if (this.checkedScale.current > 0)
                    {
                        int checkedSize = (int) (size * (this.checkedScale.current + 0.5f));
                        int scx = (int) (circleBounds.current.left + (circleBounds.current.width() - checkedSize) / 2);
                        int scy = (int) (circleBounds.current.top + checkedSize / 6f);
                        rx = scx + checkedSize / 2;
                        ry = scy + checkedSize / 2;
                        canvas.rotate(180, rx, ry);
                        checkedDrawable.setAlpha(this.endAlpha.current);
                        checkedDrawable.setBounds(scx, scy, scx + checkedSize, scy + checkedSize);
                        checkedDrawable.draw(canvas);
                        canvas.rotate(-180, rx, ry);
                    }

                    canvas.restore();
                    this.circleBounds.current.offset(-this.undoBounds.left, -this.undoBounds.top);

                    if (this.outCrcleBounds.current.width() > 0)
                    {
                        this.outCrcleBounds.current.offset(this.undoBounds.left, this.undoBounds.top);
                        borderPaint.setColor(this.textPaint.getColor());
                        borderPaint.setAlpha(alphaCombine(this.endAlpha.current, this.outCrcleAlpha.current));
                        borderPaint.setStrokeWidth(2);
                        r = this.outCrcleBounds.current.height() / 2;
                        canvas.drawRoundRect(this.outCrcleBounds.current, r, r, borderPaint);
                        this.outCrcleBounds.current.offset(-this.undoBounds.left, -this.undoBounds.top);
                    }

                }
                int textWidth = (int) this.undoBounds.width() - AndroidUtilities.dp(30) - this.cell.textLayout.getWidth();
                if (this.textLayout == null)
                    this.textLayout = new StaticLayout(this.text, textPaint, textWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                if (this.linkTextLayout == null)
                    this.linkTextLayout = new StaticLayout(this.cell.name + ".", textPaint, (int) cellSize * 2, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);

                float tx = this.undoBounds.left + padding + this.cell.cellHintBounds.height() + this.padding;
                float ty = this.undoBounds.top + padding * 2;
                canvas.translate(tx, ty);
                this.textLayout.draw(canvas);


                this.textPaint.setColor(undo_cancelColor);
                this.setTextAlpha();
                float offsetX = this.textLayout.getLineWidth(0);
                this.linkBounds.set(tx + offsetX, ty, tx + offsetX + this.linkTextLayout.getWidth(), ty + this.linkTextLayout.getHeight());
                canvas.translate(offsetX, 0);
                this.linkTextLayout.draw(canvas);
                canvas.translate(-offsetX, 0);

                canvas.translate(-tx, ty);

                backPaint.setAlpha(oldAlpha);
                borderPaint.setAlpha(ba);
                borderPaint.setMaskFilter(null);
                backPaint.setMaskFilter(null);
            }
        }
    }

    private interface RunnableFloat
    {
        void run(float patam);
    }

    private class Frame
    {
        public Frame(TimeInterpolator interpolator, int duration)
        {
            if (interpolator != null)
                animatorSet.setInterpolator(interpolator);
            if (duration > 0)
                animatorSet.setDuration(duration);
            animatorSet.addListener(new AnimatorListenerAdapter()
            {
                public void onAnimationStart(Animator animation)
                {
                    if (started != null)
                        started.run();
                }

                @Override
                public void onAnimationEnd(Animator animation)
                {
                    if (ended != null)
                        ended.run();
                }
            });
        }

        public Frame()
        {
            this(null, 0);
        }

        public Runnable ended;
        public Runnable started;
        private final AnimatorSet animatorSet = new AnimatorSet();
        private final ArrayList<Animator> animators = new ArrayList<>();

        public void add(Animator animator)
        {
            this.animators.add(animator);
        }

        public void start()
        {
            this.animatorSet.playTogether(this.animators);
            this.animatorSet.start();
        }
    }

    private class Amimator<T> extends ValueAnimator
    {
        public Amimator(T start, T end)
        {
            this.start = start;
            this.end = end;
        }

        public final T start;
        public final T end;
    }

    private abstract class AnimationInfo<T>
    {
        public AnimationInfo(T current)
        {
            this.current = current;
        }

        protected T start;
        protected T end;
        protected T current;
        public Runnable started;
        public Runnable ended;

        public void set(T start, T end)
        {
            this.start = start;
            this.end = end;
            this.current = start;
        }

        public final void update(float value)
        {
            this.onUpdated(value);
            this.onUpdated();
        }

        public final void update2(float value)
        {
            this.onUpdated2(value);
            this.onUpdated();
        }

        protected void onUpdated(float value)
        {
            current = (T) (Object) value;
        }

        protected void onUpdated2(float value)
        {
            current = (T) (Object) getValue((Float) start, (Float) end, value);
        }

        protected void onUpdated()
        {
        }

        public final Amimator<T> create(T start, T end, float startValue, float endValue, TimeInterpolator interpolator, int duration, int delay, RunnableFloat updated)
        {
            this.set(start, end);
            Amimator<T> animator = new Amimator<>(start, end);
            animator.setFloatValues(startValue, endValue);
            if (interpolator != null)
                animator.setInterpolator(interpolator);
            if (duration > 0)
                animator.setDuration(duration);
            if (delay > 0)
                animator.setStartDelay(delay);
            animator.addListener(new AnimatorListenerAdapter()
            {
                @Override
                public void onAnimationStart(Animator animation)
                {
                    Amimator<T> a = (Amimator<T>) animation;
                    set(a.start, a.end);
                    if (started != null)
                        started.run();
                }

                @Override
                public void onAnimationEnd(Animator animation)
                {
                    if (ended != null)
                        ended.run();
                }
            });
            animator.addUpdateListener((animation) ->
            {
                Amimator<T> a = (Amimator<T>) animation;
                this.start = a.start;
                this.end = a.end;
                if (updated != null)
                    updated.run((Float) animation.getAnimatedValue());
                else
                    this.update((Float) animation.getAnimatedValue());
            });
            return animator;
        }

        public Amimator<T> create(T start, T end, TimeInterpolator interpolator, int duration, int delay, RunnableFloat updated)
        {
            return create(start, end, 0, 1, interpolator, duration, delay, updated);
        }

        public final Amimator<T> create(T start, T end, int duration, int delay, RunnableFloat updated)
        {
            return create(start, end, null, duration, delay, updated);
        }

        public final Amimator<T> create(T start, T end, int duration, RunnableFloat updated)
        {
            return create(start, end, null, duration, 0, updated);
        }

        public final Amimator<T> create(T start, T end, TimeInterpolator interpolator, int duration, int delay)
        {
            return create(start, end, interpolator, duration, delay, null);
        }

        public final Amimator<T> create(T start, T end, int duration, int delay)
        {
            return create(start, end, null, duration, delay, null);
        }

        public final Amimator<T> create(T start, T end, TimeInterpolator interpolator, int duration)
        {
            return create(start, end, interpolator, duration, 0, null);
        }

        public final Amimator<T> create(T start, T end, int duration)
        {
            return create(start, end, null, duration, 0, null);
        }

        public final Amimator<T> create(T start, T end, TimeInterpolator interpolator, int duration, RunnableFloat updated)
        {
            return create(start, end, interpolator, duration, 0, updated);
        }

        public final Amimator<T> create(T start, T end, TimeInterpolator interpolator)
        {
            return create(start, end, interpolator, 0, 0, null);
        }

        public final Amimator<T> create(T start, T end)
        {
            return create(start, end, null, 0, 0, null);
        }

        public final Amimator<T> create(T start, T end, RunnableFloat updated)
        {
            return create(start, end, null, 0, 0, updated);
        }

        public final Amimator<T> create(T start, T end, TimeInterpolator interpolator, RunnableFloat updated)
        {
            return create(start, end, interpolator, 0, 0, updated);
        }
    }

    private class FloatAnimationInfo extends AnimationInfo<Float>
    {
        public FloatAnimationInfo(float current)
        {
            super(current);
        }

        public FloatAnimationInfo()
        {
            super(0f);
        }

        @Override
        public Amimator<Float> create(Float start, Float end, TimeInterpolator interpolator, int duration, int delay, RunnableFloat updated)
        {
            return super.create(start, end, start, end, interpolator, duration, delay, updated);
        }
    }

    private class IntegerAnimationInfo extends AnimationInfo<Integer>
    {
        public IntegerAnimationInfo(int current)
        {
            super(current);
        }

        public IntegerAnimationInfo()
        {
            super(0);
        }

        @Override
        protected void onUpdated(float value)
        {
            this.current = (int) getValue(this.start, this.end, value);
        }
    }

    private class ColorAnimationInfo extends AnimationInfo<Integer>
    {
        public ColorAnimationInfo(int current)
        {
            super(current);
        }

        public ColorAnimationInfo()
        {
            super(0);
        }

        @Override
        protected void onUpdated(float value)
        {
            this.current = getColor(this.start, this.end, value);
        }
    }

    private class RectAnimationInfo extends AnimationInfo<RectF>
    {
        public RectAnimationInfo()
        {
            super(new RectF());
            this.start = new RectF();
            this.end = new RectF();
        }

        @Override
        protected void onUpdated(float value)
        {
            current.set(getValue(start.left, end.left, value),
                    getValue(start.top, end.top, value),
                    getValue(start.right, end.right, value),
                    getValue(start.bottom, end.bottom, value));
        }

        @Override
        public void set(RectF start, RectF end)
        {
            this.current.set(start);
            this.start.set(start);
            this.end.set(end);
        }

        @Override
        public Amimator<RectF> create(RectF start, RectF end, TimeInterpolator interpolator, int duration, int delay, RunnableFloat updated)
        {
            return super.create(new RectF(start), new RectF(end), interpolator, duration, delay, updated);
        }

        public void updateHor(float value)
        {
            this.current.left = getValue(this.start.left, this.end.left, value);
            this.current.right = getValue(this.start.right, this.end.right, value);
            this.onUpdated();
        }

        public void updateVert(float value)
        {
            this.current.top = getValue(this.start.top, this.end.top, value);
            this.current.bottom = getValue(this.start.bottom, this.end.bottom, value);
            this.onUpdated();
        }

        public void updateLeft(float value)
        {
            this.current.left = getValue(this.start.left, this.end.left, value);
            this.current.right = this.current.left + getValue(this.start.width(), this.end.width(), value);
            this.onUpdated();
        }

        public void updateTop(float value)
        {
            this.current.top = getValue(this.start.top, this.end.top, value);
            this.current.bottom = this.current.top + getValue(this.start.height(), this.end.height(), value);
            this.onUpdated();
        }
    }

}
