/*******************************************************************************
 * Copyright 2011, 2012 Chris Banes.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/

package net.mikaelzero.mojito.view.sketch.core.zoom;

import android.content.Context;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;

import androidx.annotation.NonNull;

import net.mikaelzero.mojito.view.sketch.core.util.SketchUtils;
import net.mikaelzero.mojito.view.sketch.core.viewfun.FunctionCallbackView;

class TapHelper extends GestureDetector.SimpleOnGestureListener {

    @NonNull
    private ImageZoomer imageZoomer;
    @NonNull
    private GestureDetector tapGestureDetector;

    TapHelper(@NonNull Context appContext, @NonNull ImageZoomer imageZoomer) {
        this.imageZoomer = imageZoomer;
        this.tapGestureDetector = new GestureDetector(appContext, this);
    }

    boolean onTouchEvent(@NonNull MotionEvent event) {
        return tapGestureDetector.onTouchEvent(event);
    }

    @Override
    public boolean onDown(@NonNull MotionEvent e) {
        return true;
    }

    @Override
    public boolean onSingleTapConfirmed(@NonNull MotionEvent e) {
        ImageView imageView = imageZoomer.getImageView();
        ImageZoomer.OnViewTapListener tapListener = imageZoomer.getOnViewTapListener();
        if (tapListener != null) {
            tapListener.onViewTap(imageView, e.getX(), e.getY());
            return true;
        }

        if (imageView instanceof FunctionCallbackView) {
            FunctionCallbackView functionCallbackView = (FunctionCallbackView) imageView;
            View.OnClickListener clickListener = functionCallbackView.getOnClickListener();
            if (clickListener != null && functionCallbackView.isClickable()) {
                clickListener.onClick(imageView);
                return true;
            }
        }

        return false;
    }

    @Override
    public void onLongPress(@NonNull MotionEvent e) {
        super.onLongPress(e);

        ImageView imageView = imageZoomer.getImageView();
        ImageZoomer.OnViewLongPressListener longPressListener = imageZoomer.getOnViewLongPressListener();
        if (longPressListener != null) {
            longPressListener.onViewLongPress(imageView, e.getX(), e.getY());
            return;
        }

        if (imageView instanceof FunctionCallbackView) {
            FunctionCallbackView functionCallbackView = (FunctionCallbackView) imageView;
            View.OnLongClickListener longClickListener = functionCallbackView.getOnLongClickListener();
            if (longClickListener != null && functionCallbackView.isLongClickable()) {
                longClickListener.onLongClick(imageView);
            }
        }
    }

    @Override
    public boolean onDoubleTap(@NonNull MotionEvent ev) {
        try {
            ZoomScales zoomScales = imageZoomer.getZoomScales();
            float initScale = zoomScales.getInitZoomScale();
            float maxScale = zoomScales.getMaxZoomScale();
            float currentScale = imageZoomer.getZoomScale();

            // 已经放大过就归位到初始的完整显示比例，没放大过就放大到最大档。
            //
            // 原来的实现是"取比当前比例大一档"（getZoomScales() 返回的是 [min, max] 两档），
            // 在 min/max 之间来回跳：只要用户先用双指捏到一个中间比例，双击就只会继续往上跳，
            // 回不到原比例，双击也就做不到"再双击归位"。
            float finalScale = currentScale > initScale + 0.01f ? initScale : maxScale;

            // 以双击点为缩放中心（原来的 zoom(scale, animate) 是以屏幕中心为缩放中心，
            // 双击画面边缘的内容时会跑偏）
            imageZoomer.zoom(finalScale, ev.getX(), ev.getY(), true);
        } catch (ArrayIndexOutOfBoundsException e) {
            // Can sometimes happen when getX() and getY() is called
        }

        return true;
    }
}
