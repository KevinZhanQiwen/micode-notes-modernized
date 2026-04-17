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

package net.micode.notes.widget;

import android.appwidget.AppWidgetManager;
import android.content.Context;

import net.micode.notes.R;
import net.micode.notes.data.Notes;
import net.micode.notes.tool.ResourceParser;

/**
 * 2x2 尺寸桌面小部件
 *
 * 特点：
 * - 尺寸：2x2 网格（约 80x80 dp）
 * - 显示内容：单条笔记的摘要
 * - 点击行为：打开关联的笔记，无关联时新建笔记
 *
 * 注册信息（AndroidManifest.xml）：
 * - 名称：@string/app_widget2x2
 * - 元数据：@xml/widget_2x_info（定义更新周期、初始尺寸等）
 * - 监听广播：APPWIDGET_UPDATE、APPWIDGET_DELETED、PRIVACY_MODE_CHANGED
 *
 * 设计模式：模板方法模式的具体实现类
 * - 继承 NoteWidgetProvider，实现抽象方法
 */
public class NoteWidgetProvider_2x extends NoteWidgetProvider {

    /**
     * 系统调用此方法更新小部件
     *
     * 触发时机：
     * - 小部件首次添加到桌面
     * - 达到指定的更新周期
     * - 收到 APPWIDGET_UPDATE 广播
     *
     * @param context 上下文
     * @param appWidgetManager AppWidgetManager 实例
     * @param appWidgetIds 要更新的小部件 ID 数组
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // 调用父类的 update 方法（非隐私模式）
        super.update(context, appWidgetManager, appWidgetIds);
    }

    /**
     * 返回 2x2 小部件的布局文件 ID
     *
     * 布局文件内容（推断）：
     * - widget_bg_image：背景图片 ImageView
     * - widget_text：显示摘要的 TextView
     *
     * @return R.layout.widget_2x
     */
    @Override
    protected int getLayoutId() {
        return R.layout.widget_2x;
    }

    /**
     * 返回 2x2 小部件的背景资源 ID
     *
     * 根据背景颜色 ID（0-4）从 WidgetBgResources 获取对应的资源
     *
     * @param bgId 背景颜色 ID（0=黄，1=蓝，2=白，3=绿，4=红）
     * @return 对应的 drawable 资源 ID
     */
    @Override
    protected int getBgResourceId(int bgId) {
        return ResourceParser.WidgetBgResources.getWidget2xBgResource(bgId);
    }

    /**
     * 返回 2x2 小部件类型常量
     *
     * @return Notes.TYPE_WIDGET_2X (值为 0)
     */
    @Override
    protected int getWidgetType() {
        return Notes.TYPE_WIDGET_2X;
    }
}