/*
 * Copyright (c) 2010-2011, The MiCode Open Source Community (www.micode.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.micode.notes.ui;

import android.content.Context;
import android.graphics.Rect;
import android.text.Layout;
import android.text.Selection;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.URLSpan;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.MenuItem.OnMenuItemClickListener;
import android.view.MotionEvent;
import android.widget.EditText;

import net.micode.notes.R;

import java.util.HashMap;
import java.util.Map;

/**
 * 自定义清单模式输入框
 * 功能：
 * 1. 支持清单模式下：回车新增一行、删除空行时合并到上一行
 * 2. 支持点击链接（电话/网址/邮箱）弹出系统操作菜单
 * 3. 监听焦点与文本变化，控制勾选框显示/隐藏
 * 4. 精确触摸定位光标位置
 *
 * 用于：NoteEditActivity 的清单模式（CHECK_LIST_MODE）
 */
public class NoteEditText extends EditText {
    private static final String TAG = "NoteEditText";

    // 当前输入框在清单列表中的索引位置
    private int mIndex;
    // 删除前记录光标位置，用于判断是否需要删除整行
    private int mSelectionStartBeforeDelete;

    // 链接协议类型
    private static final String SCHEME_TEL   = "tel:";      // 电话
    private static final String SCHEME_HTTP  = "http:";     // 网址
    private static final String SCHEME_EMAIL = "mailto:";   // 邮箱

    // 链接协议 → 菜单文字 映射
    private static final Map<String, Integer> sSchemaActionResMap = new HashMap<>();
    static {
        sSchemaActionResMap.put(SCHEME_TEL,   R.string.note_link_tel);
        sSchemaActionResMap.put(SCHEME_HTTP,  R.string.note_link_web);
        sSchemaActionResMap.put(SCHEME_EMAIL, R.string.note_link_email);
    }

    /**
     * 文本变化回调接口
     * 由 NoteEditActivity 实现，处理：删除行、新增行、显示/隐藏勾选框
     */
    public interface OnTextViewChangeListener {
        /**
         * 删除当前行（内容为空且按删除键）
         */
        void onEditTextDelete(int index, String text);

        /**
         * 回车新增一行
         */
        void onEditTextEnter(int index, String text);

        /**
         * 文本内容变化（空/非空），控制勾选框显示
         */
        void onTextChange(int index, boolean hasText);
    }

    private OnTextViewChangeListener mOnTextViewChangeListener;

    public NoteEditText(Context context) {
        super(context, null);
        mIndex = 0;
    }

    /**
     * 设置当前输入框在列表中的索引
     */
    public void setIndex(int index) {
        mIndex = index;
    }

    /**
     * 设置文本变化监听器
     */
    public void setOnTextViewChangeListener(OnTextViewChangeListener listener) {
        mOnTextViewChangeListener = listener;
    }

    public NoteEditText(Context context, AttributeSet attrs) {
        super(context, attrs, android.R.attr.editTextStyle);
    }

    public NoteEditText(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    /**
     * 触摸事件：精确设置光标位置（点击哪里光标去哪里）
     */
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            int x = (int) event.getX();
            int y = (int) event.getY();

            // 减去内边距，加上滚动偏移
            x -= getTotalPaddingLeft();
            y -= getTotalPaddingTop();
            x += getScrollX();
            y += getScrollY();

            // 获取文字布局，计算行和偏移，设置光标
            Layout layout = getLayout();
            int line = layout.getLineForVertical(y);
            int off = layout.getOffsetForHorizontal(line, x);
            Selection.setSelection(getText(), off);
        }
        return super.onTouchEvent(event);
    }

    /**
     * 按键按下：记录删除前光标位置
     */
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                // 记录删除前光标位置
                mSelectionStartBeforeDelete = getSelectionStart();
                break;
            default:
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 按键抬起：处理回车新增、删除合并
     */
    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                // 删除键：如果光标在最前面，且不是第一行 → 删除当前行，合并到上一行
                if (mOnTextViewChangeListener != null) {
                    if (mSelectionStartBeforeDelete == 0 && mIndex != 0) {
                        mOnTextViewChangeListener.onEditTextDelete(mIndex, getText().toString());
                        return true;
                    }
                }
                break;

            case KeyEvent.KEYCODE_ENTER:
                // 回车键：分割文本，新增一行
                if (mOnTextViewChangeListener != null) {
                    int selStart = getSelectionStart();
                    // 光标后内容切分到新行
                    String text = getText().subSequence(selStart, length()).toString();
                    // 当前行只保留光标前内容
                    setText(getText().subSequence(0, selStart));
                    // 通知上一层新增一行
                    mOnTextViewChangeListener.onEditTextEnter(mIndex + 1, text);
                    return true;
                }
                break;

            default:
                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    /**
     * 焦点变化：内容为空时隐藏勾选框，非空显示勾选框
     */
    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        if (mOnTextViewChangeListener != null) {
            // 失去焦点 + 内容为空 → 隐藏勾选框
            if (!focused && TextUtils.isEmpty(getText())) {
                mOnTextViewChangeListener.onTextChange(mIndex, false);
            } else {
                mOnTextViewChangeListener.onTextChange(mIndex, true);
            }
        }
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
    }

    /**
     * 创建长按菜单：识别链接（电话/网址/邮箱）并弹出对应操作
     */
    @Override
    protected void onCreateContextMenu(ContextMenu menu) {
        if (getText() instanceof Spanned) {
            int selStart = getSelectionStart();
            int selEnd = getSelectionEnd();
            int min = Math.min(selStart, selEnd);
            int max = Math.max(selStart, selEnd);

            // 获取选中区域的链接
            final URLSpan[] urls = ((Spanned) getText()).getSpans(min, max, URLSpan.class);
            if (urls.length == 1) {
                String url = urls[0].getURL();
                int resId = 0;

                // 匹配链接类型
                for (String schema : sSchemaActionResMap.keySet()) {
                    if (url.startsWith(schema)) {
                        resId = sSchemaActionResMap.get(schema);
                        break;
                    }
                }

                // 默认未知链接
                if (resId == 0) {
                    resId = R.string.note_link_other;
                }

                // 添加菜单，点击触发系统意图
                menu.add(0, 0, 0, resId).setOnMenuItemClickListener(item -> {
                    urls[0].onClick(NoteEditText.this);
                    return true;
                });
            }
        }
        super.onCreateContextMenu(menu);
    }
}