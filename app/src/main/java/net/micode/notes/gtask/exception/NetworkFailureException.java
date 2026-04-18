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

package net.micode.notes.gtask.exception;

/**
 * 网络失败异常（受检异常）
 *
 * 继承 Exception，属于受检异常（Checked Exception）
 * 调用方必须显式捕获或声明抛出
 *
 * 使用场景：
 * - 无网络连接
 * - HTTP 请求超时
 * - 服务器返回错误状态码
 * - 网络 I/O 异常
 *
 * 设计意图：
 * 网络问题通常是可恢复的（如重试、提示用户检查网络），
 * 因此强制调用方处理此类异常，避免静默失败
 *
 * 与 ActionFailureException 的区别：
 * - ActionFailureException：逻辑错误，不可恢复，非受检
 * - NetworkFailureException：网络问题，可恢复，受检
 */
public class NetworkFailureException extends Exception {
    private static final long serialVersionUID = 2107610287180234136L;

    public NetworkFailureException() {
        super();
    }

    public NetworkFailureException(String paramString) {
        super(paramString);
    }

    public NetworkFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}