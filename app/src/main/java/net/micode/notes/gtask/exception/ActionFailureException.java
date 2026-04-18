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
 * 操作失败异常（非受检异常）
 *
 * 继承 RuntimeException，属于非受检异常（Unchecked Exception）
 * 调用方不必显式捕获或声明抛出
 *
 * 使用场景：
 * - JSON 解析失败
 * - 数据库操作失败
 * - 同步状态异常
 * - 数据格式错误
 *
 * 设计意图：
 * 这类异常通常表示代码逻辑错误或数据一致性问题，
 * 属于不可恢复的错误，应崩溃或记录日志，
 * 不适合在每一层都进行 try-catch 处理
 *
 * 与 NetworkFailureException 的区别：
 * - ActionFailureException：逻辑错误，不可恢复，非受检
 * - NetworkFailureException：网络问题，可恢复，受检
 */
public class ActionFailureException extends RuntimeException {
    private static final long serialVersionUID = 4425249765923293627L;

    public ActionFailureException() {
        super();
    }

    public ActionFailureException(String paramString) {
        super(paramString);
    }

    public ActionFailureException(String paramString, Throwable paramThrowable) {
        super(paramString, paramThrowable);
    }
}