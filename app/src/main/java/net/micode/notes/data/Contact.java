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

package net.micode.notes.data;

import android.content.Context;
import android.database.Cursor;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.Data;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * 联系人查询工具类
 *
 * 功能：根据电话号码查询联系人姓名，带内存缓存
 * 使用场景：通话记录笔记中显示来电/去电的联系人姓名
 *
 * 设计特点：
 * 1. 使用 HashMap 作为内存缓存，避免重复查询数据库
 * 2. 使用 PhoneNumberUtils.toCallerIDMinMatch() 进行号码模糊匹配
 * 3. 查询系统联系人数据库（ContactsContract）
 */
public class Contact {
    /** 缓存：电话号码 → 联系人姓名，避免重复查询 */
    private static HashMap<String, String> sContactCache;

    private static final String TAG = "Contact";

    /**
     * 联系人查询 SQL 选择条件模板
     *
     * 解析：
     * - PHONE_NUMBERS_EQUAL(Phone.NUMBER, ?) : 电话号码匹配（支持国际号码格式）
     * - Data.MIMETYPE = 'vnd.android.cursor.item/phone_v2' : 只查询电话号码类型的数据
     * - Data.RAW_CONTACT_ID IN (SELECT raw_contact_id FROM phone_lookup WHERE min_match = '+')
     *      : 确保匹配的是完整号码而不是子串，'+' 表示需要完全匹配
     *
     * 注意：最后的 '+' 会被 toCallerIDMinMatch() 的返回值替换
     */
    private static final String CALLER_ID_SELECTION = "PHONE_NUMBERS_EQUAL(" + Phone.NUMBER
            + ",?) AND " + Data.MIMETYPE + "='" + Phone.CONTENT_ITEM_TYPE + "'"
            + " AND " + Data.RAW_CONTACT_ID + " IN "
            + "(SELECT raw_contact_id "
            + " FROM phone_lookup"
            + " WHERE min_match = '+')";

    /**
     * 根据电话号码获取联系人姓名
     *
     * @param context 上下文，用于获取 ContentResolver
     * @param phoneNumber 电话号码
     * @return 联系人姓名，如果找不到则返回 null
     */
    public static String getContact(Context context, String phoneNumber) {
        // 懒加载初始化缓存
        if(sContactCache == null) {
            sContactCache = new HashMap<String, String>();
        }

        // 命中缓存，直接返回
        if(sContactCache.containsKey(phoneNumber)) {
            return sContactCache.get(phoneNumber);
        }

        /**
         * 将模板中的 '+' 替换为电话号码的最小匹配格式
         * toCallerIDMinMatch() 会将号码标准化，例如：
         * "13800138000" → "13800138000"（取最后7位？具体取决于实现）
         * 目的是实现号码的容错匹配（如带国际区号和不带区号的匹配）
         */
        String selection = CALLER_ID_SELECTION.replace("+",
                PhoneNumberUtils.toCallerIDMinMatch(phoneNumber));

        // 查询联系人数据库
        Cursor cursor = context.getContentResolver().query(
                Data.CONTENT_URI,                    // 查询 Data 表
                new String [] { Phone.DISPLAY_NAME }, // 只获取显示名称
                selection,                           // 选择条件
                new String[] { phoneNumber },        // 替换 ? 的参数
                null);                               // 不需要排序

        if (cursor != null && cursor.moveToFirst()) {
            try {
                String name = cursor.getString(0);  // 获取第一列：联系人姓名
                sContactCache.put(phoneNumber, name); // 存入缓存
                return name;
            } catch (IndexOutOfBoundsException e) {
                Log.e(TAG, " Cursor get string error " + e.toString());
                return null;
            } finally {
                cursor.close();  // 确保关闭游标，释放资源
            }
        } else {
            Log.d(TAG, "No contact matched with number:" + phoneNumber);
            return null;
        }
    }
}