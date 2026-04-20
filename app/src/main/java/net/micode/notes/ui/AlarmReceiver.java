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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * 笔记提醒广播接收器
 *
 * 功能：
 * 1. 接收系统 AlarmManager 发出的提醒广播
 * 2. 跳转到提醒弹窗界面（AlarmAlertActivity）
 * 3. 必须使用 NEW_TASK 标记，因为广播接收器不属于任何Activity栈
 *
 * 作用：连接「系统定时」与「提醒弹窗」的桥梁
 */
public class AlarmReceiver extends BroadcastReceiver {

    /**
     * 收到广播时触发
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // 将目标页面设置为提醒弹窗Activity
        intent.setClass(context, AlarmAlertActivity.class);

        // 必须添加此标记：广播中启动Activity需要新的任务栈
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // 启动提醒界面
        context.startActivity(intent);
    }
}