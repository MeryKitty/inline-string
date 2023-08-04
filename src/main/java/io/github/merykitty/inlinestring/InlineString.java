/*
 * Copyright (c) 1994, 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package io.github.merykitty.inlinestring;

import java.io.UnsupportedEncodingException;
import java.lang.constant.Constable;
import java.lang.constant.DynamicConstantDesc;
import java.lang.foreign.SegmentAllocator;
import java.lang.foreign.MemorySegment.Scope;
import java.nio.charset.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.*;

import io.github.merykitty.inlinestring.encoding.Unicode;
import io.github.merykitty.inlinestring.encoding.string.StringDecoder;
import io.github.merykitty.inlinestring.encoding.utf16.UTF16Decoder;
import io.github.merykitty.inlinestring.encoding.utf16.UTF16Encoder;
import io.github.merykitty.inlinestring.encoding.utf32.UTF32Decoder;
import io.github.merykitty.inlinestring.internal.*;
import io.github.merykitty.inlinestring.internal.StringUTF16;
import jdk.incubator.vector.*;

/**
 * The {@code String} class represents character strings. All
 * string literals in Java programs, such as {@code "abc"}, are
 * implemented as instances of this class.
 * <p>
 * Strings are constant; their values cannot be changed after they
 * are created. String buffers support mutable strings.
 * Because String objects are immutable they can be shared. For example:
 * <blockquote><pre>
 *     String str = "abc";
 * </pre></blockquote><p>
 * is equivalent to:
 * <blockquote><pre>
 *     char data[] = {'a', 'b', 'c'};
 *     String str = new String(data);
 * </pre></blockquote><p>
 * Here are some more examples of how strings can be used:
 * <blockquote><pre>
 *     System.out.println("abc");
 *     String cde = "cde";
 *     System.out.println("abc" + cde);
 *     String c = "abc".substring(2, 3);
 *     String d = cde.substring(1, 2);
 * </pre></blockquote>
 * <p>
 * The class {@code String} includes methods for examining
 * individual characters of the sequence, for comparing strings, for
 * searching strings, for extracting substrings, and for creating a
 * copy of a string with all characters translated to uppercase or to
 * lowercase. Case mapping is based on the Unicode Standard version
 * specified by the {@link java.lang.Character Character} class.
 * <p>
 * The Java language provides special support for the string
 * concatenation operator (&nbsp;+&nbsp;), and for conversion of
 * other objects to strings. For additional information on string
 * concatenation and conversion, see <i>The Java Language Specification</i>.
 *
 * <p> Unless otherwise noted, passing a {@code null} argument to a constructor
 * or method in this class will cause a {@link NullPointerException} to be
 * thrown.
 *
 * <p>A {@code String} represents a string in the UTF-16 format
 * in which <em>supplementary characters</em> are represented by <em>surrogate
 * pairs</em> (see the section <a href="Character.html#unicode">Unicode
 * Character Representations</a> in the {@code Character} class for
 * more information).
 * Index values refer to {@code char} code units, so a supplementary
 * character uses two positions in a {@code String}.
 * <p>The {@code String} class provides methods for dealing with
 * Unicode code points (i.e., characters), in addition to those for
 * dealing with Unicode code units (i.e., {@code char} values).
 *
 * <p>Unless otherwise noted, methods for comparing Strings do not take locale
 * into account.  The {@link java.text.Collator} class provides methods for
 * finer-grain, locale-sensitive String comparison.
 *
 * @implNote The implementation of the string concatenation operator is left to
 * the discretion of a Java compiler, as long as the compiler ultimately conforms
 * to <i>The Java Language Specification</i>. For example, the {@code javac} compiler
 * may implement the operator with {@code StringBuffer}, {@code StringBuilder},
 * or {@code java.lang.invoke.StringConcatFactory} depending on the JDK version. The
 * implementation of string conversion is typically through the method {@code toString},
 * defined by {@code Object} and inherited by all classes in Java.
 *
 * @author  Lee Boynton
 * @author  Arthur van Hoff
 * @author  Martin Buchholz
 * @author  Ulf Zibis
 * @see     java.lang.Object#toString()
 * @see     java.lang.StringBuffer
 * @see     java.lang.StringBuilder
 * @see     java.nio.charset.Charset
 * @since   1.0
 * @jls     15.18.1 String Concatenation Operator +
 */

public class InlineString
        implements CharSequence, Constable, Comparable<InlineString> {
    /**
     * If the string is compressible, this field contains the first half of the
     * value.
     *
     * <p>Otherwise, it contains the address of the buffer containing the content
     * of the string
     */
    private final long firstHalf;

    /**
     * If the string is compressible, this field contains the second half of
     * the value. The highest byte is the length of the string.
     *
     * <p>Otherwise, it contains the negated length of the string.
     */
    private final long secondHalf;

    /**
     * If the buffer is implicitly kept alive by this string, then this field
     * contains the scope corresponding to the allocation of the buffer.
     *
     * <p>Otherwise, it is null.
     */
    private final Scope scope;

    static final InlineString EMPTY_STRING = new InlineString();

    /**
     * Initializes an {@code InlineString} object so that it represents
     * an empty character sequence.
     */
    public InlineString() {
        this.firstHalf = 0;
        this.secondHalf = 0;
        this.scope = null;
    }

    public InlineString(String original) {
        this(original, DefaultAllocator.get());
    }

    /**
     * Initializes an {@code InlineString} object so that it represents
     * the same sequence of characters as a {@code String} object.
     *
     * @param  original
     *         A {@code String}
     */
    public InlineString(String original, SegmentAllocator allocator) {
        this(StringDecoder.apply(original, allocator));
    }

    public InlineString(char[] value) {
        this(value, 0, value.length, DefaultAllocator.get());
    }

    /**
     * Initializes an {@code InlineString} so that it represents the sequence
     * of characters currently contained in the character array argument. The
     * contents of the character array are copied; subsequent modification of
     * the character array does not affect the newly created string.
     *
     * @param  value
     *         The initial value of the string
     */
    public InlineString(char[] value, SegmentAllocator allocator) {
        this(value, 0, value.length, allocator);
    }

    public InlineString(char[] value, int offset, int count) {
        this(value, offset, count, DefaultAllocator.get());
    }

    /**
     * Initialize an {@code InlineString} that contains characters from a
     * subarray of the character array argument. The {@code offset} argument
     * is the index of the first character of the subarray and the
     * {@code count} argument specifies the length of the subarray. The
     * contents of the subarray are copied; subsequent modification of the
     * character array does not affect the newly created string.
     *
     * @param  value
     *         Array that is the source of characters
     *
     * @param  offset
     *         The initial offset
     *
     * @param  count
     *         The length
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code offset} is negative, {@code count} is negative, or
     *          {@code offset} is greater than {@code value.length - count}
     */
    public InlineString(char[] value, int offset,
                        int count, SegmentAllocator allocator) {
        this(UTF16Decoder.apply(value, offset, count, allocator));
    }

    public InlineString(int[] codePoints, int offset, int count) {
        this(codePoints, offset, count, DefaultAllocator.get());
    }

    /**
     * Initialize an {@code InlineString} that contains characters from a subarray
     * of the <a href="Character.html#unicode">Unicode code point</a> array
     * argument.  The {@code offset} argument is the index of the first code
     * point of the subarray and the {@code count} argument specifies the
     * length of the subarray.  The contents of the subarray are converted to
     * {@code char}s; subsequent modification of the {@code int} array does not
     * affect the newly created string.
     *
     * @param  codePoints
     *         Array that is the source of Unicode code points
     *
     * @param  offset
     *         The initial offset
     *
     * @param  count
     *         The length
     *
     * @throws  IllegalArgumentException
     *          If any invalid Unicode code point is found in {@code
     *          codePoints}
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code offset} is negative, {@code count} is negative, or
     *          {@code offset} is greater than {@code codePoints.length - count}
     *
     * @since  1.5
     */
    public InlineString(int[] codePoints, int offset,
                        int count, SegmentAllocator allocator) {
        this(UTF32Decoder.apply(codePoints, offset, count, allocator));
    }

    public InlineString(byte[] bytes, int offset, int length, String charsetName)
            throws UnsupportedEncodingException {
        this(bytes, offset, length,
                StringData.lookupCharset(charsetName), DefaultAllocator.get());
    }

    /**
     * Initialize an {@code InlineString} by decoding the specified subarray of
     * bytes using the specified charset.  The length of the new
     * {@code InlineString} is a function of the charset, and hence may not be
     * equal to the length of the subarray.
     *
     * <p> The behavior of this constructor when the given bytes are not valid
     * in the given charset is unspecified.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param  bytes
     *         The bytes to be decoded into characters
     *
     * @param  offset
     *         The index of the first byte to decode
     *
     * @param  length
     *         The number of bytes to decode
     *
     * @param  charsetName
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code offset} is negative, {@code length} is negative, or
     *          {@code offset} is greater than {@code bytes.length - length}
     *
     * @since  1.1
     */
    public InlineString(byte[] bytes, int offset, int length,
                        String charsetName, SegmentAllocator allocator)
            throws UnsupportedEncodingException {
        this(bytes, offset, length, StringData.lookupCharset(charsetName), allocator);
    }

    public InlineString(byte[] bytes, int offset, int length, Charset charset) {
        this(bytes, offset, length, charset, DefaultAllocator.get());
    }

    /**
     * Initialize an {@code InlineString} by decoding the specified subarray of
     * bytes using the specified {@linkplain java.nio.charset.Charset charset}.
     * The length of the new {@code InlineString} is a function of the charset, and
     * hence may not be equal to the length of the subarray.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param  bytes
     *         The bytes to be decoded into characters
     *
     * @param  offset
     *         The index of the first byte to decode
     *
     * @param  length
     *         The number of bytes to decode
     *
     * @param  charset
     *         The {@linkplain java.nio.charset.Charset charset} to be used to
     *         decode the {@code bytes}
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code offset} is negative, {@code length} is negative, or
     *          {@code offset} is greater than {@code bytes.length - length}
     *
     * @since  1.6
     */
    public InlineString(byte[] bytes, int offset, int length, Charset charset,
                        SegmentAllocator allocator) {
        this(Unicode.decode(bytes, offset, length, charset, allocator));
    }

    /**
     * Initialize an {@code InlineString} by decoding the specified array of bytes
     * using the specified {@linkplain java.nio.charset.Charset charset}.  The
     * length of the new {@code InlineString} is a function of the charset, and
     * hence may not be equal to the length of the byte array.
     *
     * <p> The behavior of this constructor when the given bytes are not valid
     * in the given charset is unspecified.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param  bytes
     *         The bytes to be decoded into characters
     *
     * @param  charsetName
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     *
     * @since  1.1
     */
    public InlineString(byte[] bytes, String charsetName)
            throws UnsupportedEncodingException {
        this(bytes, 0, bytes.length,
                StringData.lookupCharset(charsetName), DefaultAllocator.get());
    }

    public InlineString(byte[] bytes, String charsetName,
                        SegmentAllocator allocator)
            throws UnsupportedEncodingException {
        this(bytes, 0, bytes.length,
                StringData.lookupCharset(charsetName), allocator);
    }

    /**
     * Initialize an {@code InlineString} by decoding the specified array of
     * bytes using the specified {@linkplain java.nio.charset.Charset charset}.
     * The length of the new {@code InlineString} is a function of the charset,
     * and hence may not be equal to the length of the byte array.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement string.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param  bytes
     *         The bytes to be decoded into characters
     *
     * @param  charset
     *         The {@linkplain java.nio.charset.Charset charset} to be used to
     *         decode the {@code bytes}
     *
     * @since  1.6
     */
    public InlineString(byte[] bytes, Charset charset) {
        this(bytes, 0, bytes.length, charset, DefaultAllocator.get());
    }

    public InlineString(byte[] bytes, Charset charset,
                        SegmentAllocator allocator) {
        this(bytes, 0, bytes.length, charset, allocator);
    }

    /**
     * Initialize an {@code InlineString} by decoding the specified subarray of
     * bytes using the platform's default charset.  The length of the new
     * {@code InlineString} is a function of the charset, and hence may not be
     * equal to the length of the subarray.
     *
     * <p> The behavior of this constructor when the given bytes are not valid
     * in the default charset is unspecified.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param  bytes
     *         The bytes to be decoded into characters
     *
     * @param  offset
     *         The index of the first byte to decode
     *
     * @param  length
     *         The number of bytes to decode
     *
     * @throws  IndexOutOfBoundsException
     *          If {@code offset} is negative, {@code length} is negative, or
     *          {@code offset} is greater than {@code bytes.length - length}
     *
     * @since  1.1
     */
    public InlineString(byte[] bytes, int offset, int length) {
        this(bytes, offset, length,
                Charset.defaultCharset(), DefaultAllocator.get());
    }

    public InlineString(byte[] bytes, int offset, int length,
                        SegmentAllocator allocator) {
        this(bytes, offset, length, Charset.defaultCharset(), allocator);
    }

    /**
     * Initialize an {@code InlineString} by decoding the specified array of bytes
     * using the platform's default charset.  The length of the new {@code
     * InlineString} is a function of the charset, and hence may not be equal to
     * the length of the byte array.
     *
     * <p> The behavior of this constructor when the given bytes are not valid
     * in the default charset is unspecified.  The {@link
     * java.nio.charset.CharsetDecoder} class should be used when more control
     * over the decoding process is required.
     *
     * @param  bytes
     *         The bytes to be decoded into characters
     *
     * @since  1.1
     */
    public InlineString(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public InlineString(byte[] bytes, SegmentAllocator allocator) {
        this(bytes, 0, bytes.length, allocator);
    }

    /**
     * Initialize an {@code InlineString} that contains the sequence of
     * characters currently contained in the string buffer argument. The
     * contents of the string buffer are copied; subsequent modification of the
     * string buffer does not affect the newly created string.
     *
     * @param  buffer
     *         A {@code StringBuffer}
     */
    public InlineString(StringBuffer buffer) {
        this(buffer.toString());
    }

    /**
     * Initialize an {@code InlineString} that contains the sequence of
     * characters currently contained in the string builder argument. The
     * contents of the string builder are copied; subsequent modification of the
     * string builder does not affect the newly created string.
     *
     * @param   builder
     *          A {@code StringBuilder}
     *
     * @since  1.5
     */
    public InlineString(StringBuilder builder) {
        this(builder.toString());
    }

    /**
     * Returns the length of this string.
     * The length is equal to the number of <a href="Character.html#unicode">Unicode
     * code units</a> in the string.
     *
     * @return  the length of the sequence of characters represented by this
     *          object.
     */
    public int length() {
        return isCompressed()
                ? compressedLength()
                : nonCompressedLength();
    }

    /**
     * Returns {@code true} if, and only if, {@link #length()} is {@code 0}.
     *
     * @return {@code true} if {@link #length()} is {@code 0}, otherwise
     * {@code false}
     *
     * @since 1.6
     */
    public boolean isEmpty() {
        return secondHalf == 0;
    }

    /**
     * Returns the {@code char} value at the
     * specified index. An index ranges from {@code 0} to
     * {@code length() - 1}. The first {@code char} value of the sequence
     * is at index {@code 0}, the next at index {@code 1},
     * and so on, as for array indexing.
     *
     * <p>If the {@code char} value specified by the index is a
     * <a href="Character.html#unicode">surrogate</a>, the surrogate
     * value is returned.
     *
     * @param      index   the index of the {@code char} value.
     * @return     the {@code char} value at the specified index of this string.
     *             The first {@code char} value is at index {@code 0}.
     * @throws     IndexOutOfBoundsException  if the {@code index}
     *             argument is negative or not less than the length of this
     *             string.
     */
    public char charAt(int index) {
        if (isCompressed()) {
            StringData.checkIndex(index, compressedLength());
            return StringCompressed.charAt(firstHalf(), secondHalf(), index);
        } else {
            StringData.checkIndex(index, nonCompressedLength());
            return StringUTF16.charAt(address(), scope(), index);
        }
    }

    /**
     * Returns the character (Unicode code point) at the specified
     * index. The index refers to {@code char} values
     * (Unicode code units) and ranges from {@code 0} to
     * {@link #length()}{@code - 1}.
     *
     * <p> If the {@code char} value specified at the given index
     * is in the high-surrogate range, the following index is less
     * than the length of this {@code String}, and the
     * {@code char} value at the following index is in the
     * low-surrogate range, then the supplementary code point
     * corresponding to this surrogate pair is returned. Otherwise,
     * the {@code char} value at the given index is returned.
     *
     * @param      index the index to the {@code char} values
     * @return     the code point value of the character at the
     *             {@code index}
     * @throws     IndexOutOfBoundsException  if the {@code index}
     *             argument is negative or not less than the length of this
     *             string.
     * @since      1.5
     */
    public int codePointAt(int index) {
        if (isCompressed()) {
            StringData.checkIndex(index, compressedLength());
            return StringCompressed.charAt(firstHalf(), secondHalf(), index);
        } else {
            StringData.checkIndex(index, nonCompressedLength());
            return StringUTF16.codePointAt(address(), nonCompressedLength(), scope(), index);
        }
    }

    /**
     * Returns the character (Unicode code point) before the specified
     * index. The index refers to {@code char} values
     * (Unicode code units) and ranges from {@code 1} to {@link
     * CharSequence#length() length}.
     *
     * <p> If the {@code char} value at {@code (index - 1)}
     * is in the low-surrogate range, {@code (index - 2)} is not
     * negative, and the {@code char} value at {@code (index -
     * 2)} is in the high-surrogate range, then the
     * supplementary code point value of the surrogate pair is
     * returned. If the {@code char} value at {@code index -
     * 1} is an unpaired low-surrogate or a high-surrogate, the
     * surrogate value is returned.
     *
     * @param     index the index following the code point that should be returned
     * @return    the Unicode code point value before the given index.
     * @throws    IndexOutOfBoundsException if the {@code index}
     *            argument is less than 1 or greater than the length
     *            of this string.
     * @since     1.5
     */
    public int codePointBefore(int index) {
        index--;
        if (isCompressed()) {
            StringData.checkIndex(index, compressedLength());
            return StringCompressed.charAt(firstHalf(), secondHalf(), index);
        } else {
            StringData.checkIndex(index, nonCompressedLength());
            return StringUTF16.codePointBefore(address(), scope(), index);
        }
    }

    /**
     * Returns the number of Unicode code points in the specified text
     * range of this {@code String}. The text range begins at the
     * specified {@code beginIndex} and extends to the
     * {@code char} at index {@code endIndex - 1}. Thus the
     * length (in {@code char}s) of the text range is
     * {@code endIndex-beginIndex}. Unpaired surrogates within
     * the text range count as one code point each.
     *
     * @param beginIndex the index to the first {@code char} of
     * the text range.
     * @param endIndex the index after the last {@code char} of
     * the text range.
     * @return the number of Unicode code points in the specified text
     * range
     * @throws    IndexOutOfBoundsException if the
     * {@code beginIndex} is negative, or {@code endIndex}
     * is larger than the length of this {@code String}, or
     * {@code beginIndex} is larger than {@code endIndex}.
     * @since  1.5
     */
    public int codePointCount(int beginIndex, int endIndex) {
        if (isCompressed()) {
            StringData.checkBoundsBeginEnd(beginIndex, endIndex,
                    compressedLength());
            return endIndex - beginIndex;
        } else {
            StringData.checkBoundsBeginEnd(beginIndex, endIndex, nonCompressedLength());
            return StringUTF16.codePointCount(address(), scope(),
                    beginIndex, endIndex);
        }
    }

    /**
     * Returns the index within this {@code String} that is
     * offset from the given {@code index} by
     * {@code codePointOffset} code points. Unpaired surrogates
     * within the text range given by {@code index} and
     * {@code codePointOffset} count as one code point each.
     *
     * @param index the index to be offset
     * @param codePointOffset the offset in code points
     * @return the index within this {@code String}
     * @throws    IndexOutOfBoundsException if {@code index}
     *   is negative or larger than the length of this
     *   {@code String}, or if {@code codePointOffset} is positive
     *   and the substring starting with {@code index} has fewer
     *   than {@code codePointOffset} code points,
     *   or if {@code codePointOffset} is negative and the substring
     *   before {@code index} has fewer than the absolute value
     *   of {@code codePointOffset} code points.
     * @since 1.5
     */
    public int offsetByCodePoints(int index, int codePointOffset) {
        if (isCompressed()) {
            int length = compressedLength();
            StringData.checkOffset(index, length);
            int result = index + codePointOffset;
            StringData.checkOffset(result, length);
            return result;
        } else {
            int length = nonCompressedLength();
            StringData.checkOffset(index, length);
            return StringUTF16.offsetByCodePoints(address(), length, scope(), index, codePointOffset);
        }
    }

    /**
     * Copies characters from this string into the destination character
     * array.
     * <p>
     * The first character to be copied is at index {@code srcBegin};
     * the last character to be copied is at index {@code srcEnd-1}
     * (thus the total number of characters to be copied is
     * {@code srcEnd-srcBegin}). The characters are copied into the
     * subarray of {@code dst} starting at index {@code dstBegin}
     * and ending at index:
     * <blockquote><pre>
     *     dstBegin + (srcEnd-srcBegin) - 1
     * </pre></blockquote>
     *
     * @param      srcBegin   index of the first character in the string
     *                        to copy.
     * @param      srcEnd     index after the last character in the string
     *                        to copy.
     * @param      dst        the destination array.
     * @param      dstBegin   the start offset in the destination array.
     * @throws    IndexOutOfBoundsException If any of the following
     *            is true:
     *            <ul><li>{@code srcBegin} is negative.
     *            <li>{@code srcBegin} is greater than {@code srcEnd}
     *            <li>{@code srcEnd} is greater than the length of this
     *                string
     *            <li>{@code dstBegin} is negative
     *            <li>{@code dstBegin+(srcEnd-srcBegin)} is larger than
     *                {@code dst.length}</ul>
     */
    public void getChars(int srcBegin, int srcEnd, char[] dst, int dstBegin) {
        long count = srcEnd - srcBegin;
        Objects.checkFromIndexSize(dstBegin, count, dst.length);
        if (isCompressed()) {
            StringData.checkBoundsBeginEnd(srcBegin, srcEnd, compressedLength());
            UTF16Encoder.applyCompressed(dst, dstBegin, firstHalf(), secondHalf(), srcBegin, (int)count);
        } else {
            StringData.checkBoundsBeginEnd(srcBegin, srcEnd, nonCompressedLength());
            UTF16Encoder.applyNonCompressed(dst, dstBegin, address(), scope(), srcBegin, (int)count);
        }
    }

    /**
     * Encodes this {@code String} into a sequence of bytes using the named
     * charset, storing the result into a new byte array.
     *
     * <p> The behavior of this method when this string cannot be encoded in
     * the given charset is unspecified.  The {@link
     * java.nio.charset.CharsetEncoder} class should be used when more control
     * over the encoding process is required.
     *
     * @param  charsetName
     *         The name of a supported {@linkplain java.nio.charset.Charset
     *         charset}
     *
     * @return  The resultant byte array
     *
     * @throws  UnsupportedEncodingException
     *          If the named charset is not supported
     *
     * @since  1.1
     */
    public byte[] getBytes(String charsetName)
            throws UnsupportedEncodingException {
        Objects.requireNonNull(charsetName);
        return getBytes(StringData.lookupCharset(charsetName));
    }

    /**
     * Encodes this {@code String} into a sequence of bytes using the given
     * {@linkplain java.nio.charset.Charset charset}, storing the result into a
     * new byte array.
     *
     * <p> This method always replaces malformed-input and unmappable-character
     * sequences with this charset's default replacement byte array.  The
     * {@link java.nio.charset.CharsetEncoder} class should be used when more
     * control over the encoding process is required.
     *
     * @param  charset
     *         The {@linkplain java.nio.charset.Charset} to be used to encode
     *         the {@code String}
     *
     * @return  The resultant byte array
     *
     * @since  1.6
     */
    public byte[] getBytes(Charset charset) {
        Objects.requireNonNull(charset);
        return this.toString().getBytes(charset);
    }

    /**
     * Encodes this {@code String} into a sequence of bytes using the
     * platform's default charset, storing the result into a new byte array.
     *
     * <p> The behavior of this method when this string cannot be encoded in
     * the default charset is unspecified.  The {@link
     * java.nio.charset.CharsetEncoder} class should be used when more control
     * over the encoding process is required.
     *
     * @return  The resultant byte array
     *
     * @since      1.1
     */
    public byte[] getBytes() {
        return getBytes(Charset.defaultCharset());
    }

    /**
     * Compares this string to another string.  The result is {@code
     * true} if and only if the argument represents the same sequence
     * of characters as this object.
     *
     * <p>For finer-grained String comparison, refer to
     * {@link java.text.Collator}.
     *
     * @param  other
     *         The string to compare this {@code InlineString} against
     *
     * @return  {@code true} if the given string is equivalent to this
     * string, {@code false} otherwise
     *
     * @see  #compareTo(InlineString)
     * @see  #equalsIgnoreCase(InlineString)
     */
    public boolean equals(InlineString other) {
        if (secondHalf != other.secondHalf) {
            return false;
        }
        if (firstHalf == other.firstHalf) {
            return true;
        }
        return !isCompressed() &&
                StringUTF16.equals(address(), scope(),
                        other.address(), other.scope(), nonCompressedLength());
    }

    /**
     * Compares this string to the specified object.  The result is {@code
     * true} if and only if the argument is not {@code null} and is a {@code
     * InlineString} object that represents the same sequence of characters
     * as this object.
     *
     * <p>For finer-grained String comparison, refer to
     * {@link java.text.Collator}.
     *
     * @param  anObject
     *         The object to compare this {@code InlineString} against
     *
     * @return  {@code true} if the given object represents a {@code String}
     *          equivalent to this string, {@code false} otherwise
     *
     * @see  #compareTo(InlineString)
     * @see  #equalsIgnoreCase(InlineString)
     */
    public boolean equals(Object anObject) {
        if (anObject instanceof InlineString aString) {
            return equals(aString);
        } else {
            return false;
        }
    }

    private boolean nonSyncContentEquals(CharSequence cs) {
        int length = length();
        if (length != cs.length()) {
            return false;
        }

        for (int i = 0; i < length; i++) {
            if (charAt(i) != cs.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Compares this string to the specified {@code CharSequence}.  The
     * result is {@code true} if and only if this {@code String} represents the
     * same sequence of char values as the specified sequence. Note that if the
     * {@code CharSequence} is a {@code StringBuffer} then the method
     * synchronizes on it.
     *
     * <p>For finer-grained String comparison, refer to
     * {@link java.text.Collator}.
     *
     * @param  cs
     *         The sequence to compare this {@code String} against
     *
     * @return  {@code true} if this {@code String} represents the same
     *          sequence of char values as the specified sequence, {@code
     *          false} otherwise
     *
     * @since  1.5
     */
    public boolean contentEquals(CharSequence cs) {
        if (cs instanceof StringBuffer) {
            synchronized (cs) {
                return nonSyncContentEquals(cs);
            }
        }

        return nonSyncContentEquals(cs);
    }

    /**
     * Compares this {@code String} to another {@code String}, ignoring case
     * considerations.  Two strings are considered equal ignoring case if they
     * are of the same length and corresponding Unicode code points in the two
     * strings are equal ignoring case.
     *
     * <p> Two Unicode code points are considered the same
     * ignoring case if at least one of the following is true:
     * <ul>
     *   <li> The two Unicode code points are the same (as compared by the
     *        {@code ==} operator)
     *   <li> Calling {@code Character.toLowerCase(Character.toUpperCase(int))}
     *        on each Unicode code point produces the same result
     * </ul>
     *
     * <p>Note that this method does <em>not</em> take locale into account, and
     * will result in unsatisfactory results for certain locales.  The
     * {@link java.text.Collator} class provides locale-sensitive comparison.
     *
     * @param  other
     *         The {@code String} to compare this {@code String} against
     *
     * @return  {@code true} if the argument is not {@code null} and it
     *          represents an equivalent {@code String} ignoring case; {@code
     *          false} otherwise
     *
     * @see  #equals(Object)
     * @see  #codePoints()
     */
    public boolean equalsIgnoreCase(InlineString other) {
        int length = length();
        return (other.length() == length
                && regionMatches(true, 0, other, 0, length));
    }

    /**
     * Compares two strings lexicographically.
     * The comparison is based on the value of each character in
     * the strings. The character sequence represented by this
     * {@code String} object is compared lexicographically to the
     * character sequence represented by the argument string. The result is
     * a negative integer if this {@code String} object
     * lexicographically precedes the argument string. The result is a
     * positive integer if this {@code String} object lexicographically
     * follows the argument string. The result is zero if the strings
     * are equal; {@code compareTo} returns {@code 0} exactly when
     * the {@link #equals(Object)} method would return {@code true}.
     * <p>
     * This is the definition of lexicographic ordering. If two strings are
     * different, then either they have different characters at some index
     * that is a valid index for both strings, or their lengths are different,
     * or both. If they have different characters at one or more index
     * positions, let <i>k</i> be the smallest such index; then the string
     * whose character at position <i>k</i> has the smaller value, as
     * determined by using the {@code <} operator, lexicographically precedes the
     * other string. If there is no index position at which they differ, then the
     * shorter string lexicographically precedes the longer string.
     *
     * <p>For finer-grained String comparison, refer to
     * {@link java.text.Collator}.
     *
     * @param   other   the {@code String} to be compared.
     * @return  the value {@code 0} if the argument string is equal to
     *          this string; a value less than {@code 0} if this string
     *          is lexicographically less than the string argument; and a
     *          value greater than {@code 0} if this string is
     *          lexicographically greater than the string argument.
     */
    @Override
    public int compareTo(InlineString other) {
        if (isCompressed()) {
            if (other.isCompressed()) {
                return StringCompressed.compareToCompressed(firstHalf(), secondHalf(),
                        other.firstHalf(), other.secondHalf());
            } else {
                return StringCompressed.compareToUTF16(firstHalf(), secondHalf(),
                        other.address(), other.nonCompressedLength(), other.scope());
            }
        } else {
            if (other.isCompressed()) {
                return -StringCompressed.compareToUTF16(other.firstHalf(), other.secondHalf(),
                        address(), nonCompressedLength(), scope());
            } else {
                return StringUTF16.compareToUTF16(address(), nonCompressedLength(), scope(),
                        other.address(), other.nonCompressedLength(), other.scope());
            }
        }
    }

    /**
     * A Comparator that orders {@code InlineString} objects as by
     * {@link #compareToIgnoreCase(InlineString) compareToIgnoreCase}.
     * <p>
     * Note that this Comparator does <em>not</em> take locale into account,
     * and will result in an unsatisfactory ordering for certain locales.
     * The {@link java.text.Collator} class provides locale-sensitive comparison.
     *
     * @see     java.text.Collator
     * @since   1.2
     */
    public static final Comparator<InlineString> CASE_INSENSITIVE_ORDER
            = (s1, s2) -> {
        if (s1.isCompressed() && s2.isCompressed()) {
            return StringCompressed.compareToCICompressed(s1.firstHalf(), s1.secondHalf(),
                    s2.firstHalf(), s2.secondHalf());
        } else {
            var cursor1 = s1.codePointCursor();
            var cursor2 = s2.codePointCursor();
            while (cursor1.valid() && cursor2.valid()) {
                int c1 = Character.toLowerCase(Character.toUpperCase(cursor1.get()));
                int c2 = Character.toLowerCase(Character.toUpperCase(cursor2.get()));
                if (c1 != c2) {
                    return c1 - c2;
                }
                cursor1 = cursor1.next();
                cursor2 = cursor2.next();
            }
            return Long.compare(s1.length(), s2.length());
        }
    };

    /**
     * Compares two strings lexicographically, ignoring case
     * differences. This method returns an integer whose sign is that of
     * calling {@code compareTo} with case folded versions of the strings
     * where case differences have been eliminated by calling
     * {@code Character.toLowerCase(Character.toUpperCase(int))} on
     * each Unicode code point.
     * <p>
     * Note that this method does <em>not</em> take locale into account,
     * and will result in an unsatisfactory ordering for certain locales.
     * The {@link java.text.Collator} class provides locale-sensitive comparison.
     *
     * @param   str   the {@code String} to be compared.
     * @return  a negative integer, zero, or a positive integer as the
     *          specified String is greater than, equal to, or less
     *          than this String, ignoring case considerations.
     * @see     java.text.Collator
     * @see     #codePoints()
     * @since   1.2
     */
    public int compareToIgnoreCase(InlineString str) {
        return CASE_INSENSITIVE_ORDER.compare(this, str);
    }

    /**
     * Tests if two string regions are equal.
     * <p>
     * A substring of this {@code String} object is compared to a substring
     * of the argument other. The result is true if these substrings
     * represent identical character sequences. The substring of this
     * {@code String} object to be compared begins at index {@code toffset}
     * and has length {@code len}. The substring of other to be compared
     * begins at index {@code ooffset} and has length {@code len}. The
     * result is {@code false} if and only if at least one of the following
     * is true:
     * <ul><li>{@code toffset} is negative.
     * <li>{@code ooffset} is negative.
     * <li>{@code toffset+len} is greater than the length of this
     * {@code String} object.
     * <li>{@code ooffset+len} is greater than the length of the other
     * argument.
     * <li>There is some nonnegative integer <i>k</i> less than {@code len}
     * such that:
     * {@code this.charAt(toffset + }<i>k</i>{@code ) != other.charAt(ooffset + }
     * <i>k</i>{@code )}
     * </ul>
     *
     * <p>Note that this method does <em>not</em> take locale into account.  The
     * {@link java.text.Collator} class provides locale-sensitive comparison.
     *
     * @param   toffset   the starting offset of the subregion in this string.
     * @param   other     the string argument.
     * @param   ooffset   the starting offset of the subregion in the string
     *                    argument.
     * @param   len       the number of characters to compare.
     * @return  {@code true} if the specified subregion of this string
     *          exactly matches the specified subregion of the string argument;
     *          {@code false} otherwise.
     */
    public boolean regionMatches(int toffset, InlineString other, int ooffset, int len) {
        if (!StringData.testBoundsOffCount(toffset, len, length()) ||
                !StringData.testBoundsOffCount(ooffset, len, other.length())) {
            return false;
        }

        if (isCompressed()) {
            if (other.isCompressed()) {
                return StringCompressed.regionMatchesCompressed(firstHalf(), secondHalf(), toffset,
                        other.firstHalf(), other.secondHalf(), ooffset, len);
            } else {
                long oaddress = other.address() + ooffset * (long)Character.BYTES;
                return StringCompressed.regionMatchesUTF16(firstHalf(), secondHalf(), toffset,
                        oaddress, other.scope(), len);
            }
        } else {
            long address = address() + toffset * (long)Character.BYTES;
            if (other.isCompressed()) {
                return StringCompressed.regionMatchesUTF16(other.firstHalf(), other.secondHalf(), ooffset,
                        address, scope(), len);
            } else {
                long oaddress = other.address() + ooffset * (long)Character.BYTES;
                return StringUTF16.equals(address, scope(),
                        oaddress, other.scope(), len);
            }
        }
    }

    /**
     * Tests if two string regions are equal.
     * <p>
     * A substring of this {@code String} object is compared to a substring
     * of the argument {@code other}. The result is {@code true} if these
     * substrings represent Unicode code point sequences that are the same,
     * ignoring case if and only if {@code ignoreCase} is true.
     * The sequences {@code tsequence} and {@code osequence} are compared,
     * where {@code tsequence} is the sequence produced as if by calling
     * {@code this.substring(toffset, toffset + len).codePoints()} and
     * {@code osequence} is the sequence produced as if by calling
     * {@code other.substring(ooffset, ooffset + len).codePoints()}.
     * The result is {@code true} if and only if all of the following
     * are true:
     * <ul><li>{@code toffset} is non-negative.
     * <li>{@code ooffset} is non-negative.
     * <li>{@code toffset+len} is less than or equal to the length of this
     * {@code String} object.
     * <li>{@code ooffset+len} is less than or equal to the length of the other
     * argument.
     * <li>if {@code ignoreCase} is {@code false}, all pairs of corresponding Unicode
     * code points are equal integer values; or if {@code ignoreCase} is {@code true},
     * {@link Character#toLowerCase(int) Character.toLowerCase(}
     * {@link Character#toUpperCase(int)}{@code )} on all pairs of Unicode code points
     * results in equal integer values.
     * </ul>
     *
     * <p>Note that this method does <em>not</em> take locale into account,
     * and will result in unsatisfactory results for certain locales when
     * {@code ignoreCase} is {@code true}.  The {@link java.text.Collator} class
     * provides locale-sensitive comparison.
     *
     * @param   ignoreCase   if {@code true}, ignore case when comparing
     *                       characters.
     * @param   toffset      the starting offset of the subregion in this
     *                       string.
     * @param   other        the string argument.
     * @param   ooffset      the starting offset of the subregion in the string
     *                       argument.
     * @param   len          the number of characters (Unicode code units -
     *                       16bit {@code char} value) to compare.
     * @return  {@code true} if the specified subregion of this string
     *          matches the specified subregion of the string argument;
     *          {@code false} otherwise. Whether the matching is exact
     *          or case-insensitive depends on the {@code ignoreCase}
     *          argument.
     * @see     #codePoints()
     */
    public boolean regionMatches(boolean ignoreCase, int toffset,
                                 InlineString other, int ooffset, int len) {
        if (!ignoreCase) {
            return regionMatches(toffset, other, ooffset, len);
        }
        if (!StringData.testBoundsOffCount(toffset, len, length()) ||
                !StringData.testBoundsOffCount(ooffset, len, other.length())) {
            return false;
        }

        if (isCompressed() && other.isCompressed()) {
            return StringCompressed.regionMatchesCICompressed(firstHalf(), secondHalf(), toffset,
                    other.firstHalf(), other.secondHalf(), ooffset, len);
        } else {
            int tend = toffset + len;
            int oend = ooffset + len;
            return this.substring(toffset, tend)
                    .compareToIgnoreCase(other.substring(ooffset, oend)) == 0;
        }
    }

    /**
     * Tests if the substring of this string beginning at the
     * specified index starts with the specified prefix.
     *
     * @param   prefix    the prefix.
     * @param   toffset   where to begin looking in this string.
     * @return  {@code true} if the character sequence represented by the
     *          argument is a prefix of the substring of this object starting
     *          at index {@code toffset}; {@code false} otherwise.
     *          The result is {@code false} if {@code toffset} is
     *          negative or greater than the length of this
     *          {@code String} object; otherwise the result is the same
     *          as the result of the expression
     *          <pre>
     *          this.substring(toffset).startsWith(prefix)
     *          </pre>
     */
    public boolean startsWith(InlineString prefix, int toffset) {
        return regionMatches(toffset, prefix, 0, prefix.length());
    }

    /**
     * Tests if this string starts with the specified prefix.
     *
     * @param   prefix   the prefix.
     * @return  {@code true} if the character sequence represented by the
     *          argument is a prefix of the character sequence represented by
     *          this string; {@code false} otherwise.
     *          Note also that {@code true} will be returned if the
     *          argument is an empty string or is equal to this
     *          {@code String} object as determined by the
     *          {@link #equals(Object)} method.
     * @since   1.0
     */
    public boolean startsWith(InlineString prefix) {
        return startsWith(prefix, 0);
    }

    /**
     * Tests if this string ends with the specified suffix.
     *
     * @param   suffix   the suffix.
     * @return  {@code true} if the character sequence represented by the
     *          argument is a suffix of the character sequence represented by
     *          this object; {@code false} otherwise. Note that the
     *          result will be {@code true} if the argument is the
     *          empty string or is equal to this {@code String} object
     *          as determined by the {@link #equals(Object)} method.
     */
    public boolean endsWith(InlineString suffix) {
        return startsWith(suffix, length() - suffix.length());
    }

    /**
     * Returns a hash code for this string. The hash code for a
     * {@code String} object is computed as
     * <blockquote><pre>
     * s[0]*31^(n-1) + s[1]*31^(n-2) + ... + s[n-1]
     * </pre></blockquote>
     * using {@code int} arithmetic, where {@code s[i]} is the
     * <i>i</i>th character of the string, {@code n} is the length of
     * the string, and {@code ^} indicates exponentiation.
     * (The hash value of the empty string is zero.)
     *
     * @return  a hash code value for this object.
     */
    public int hashCode() {
        if (isCompressed()) {
            return StringCompressed.hashCode(firstHalf(), secondHalf());
        } else {
            return StringUTF16.hashCode(address(), nonCompressedLength(), scope());
        }
    }

    /**
     * Returns the index within this string of the first occurrence of
     * the specified character. If a character with value
     * {@code ch} occurs in the character sequence represented by
     * this {@code String} object, then the index (in Unicode
     * code units) of the first such occurrence is returned. For
     * values of {@code ch} in the range from 0 to 0xFFFF
     * (inclusive), this is the smallest value <i>k</i> such that:
     * <blockquote><pre>
     * this.charAt(<i>k</i>) == ch
     * </pre></blockquote>
     * is true. For other values of {@code ch}, it is the
     * smallest value <i>k</i> such that:
     * <blockquote><pre>
     * this.codePointAt(<i>k</i>) == ch
     * </pre></blockquote>
     * is true. In either case, if no such character occurs in this
     * string, then {@code -1} is returned.
     *
     * @param   ch   a character (Unicode code point).
     * @return  the index of the first occurrence of the character in the
     *          character sequence represented by this object, or
     *          {@code -1} if the character does not occur.
     */
    public int indexOf(int ch) {
        return indexOf(ch, 0);
    }

    /**
     * Returns the index within this string of the first occurrence of the
     * specified character, starting the search at the specified index.
     * <p>
     * If a character with value {@code ch} occurs in the
     * character sequence represented by this {@code String}
     * object at an index no smaller than {@code fromIndex}, then
     * the index of the first such occurrence is returned. For values
     * of {@code ch} in the range from 0 to 0xFFFF (inclusive),
     * this is the smallest value <i>k</i> such that:
     * <blockquote><pre>
     * (this.charAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &gt;= fromIndex)
     * </pre></blockquote>
     * is true. For other values of {@code ch}, it is the
     * smallest value <i>k</i> such that:
     * <blockquote><pre>
     * (this.codePointAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &gt;= fromIndex)
     * </pre></blockquote>
     * is true. In either case, if no such character occurs in this
     * string at or after position {@code fromIndex}, then
     * {@code -1} is returned.
     *
     * <p>
     * There is no restriction on the value of {@code fromIndex}. If it
     * is negative, it has the same effect as if it were zero: this entire
     * string may be searched. If it is greater than the length of this
     * string, it has the same effect as if it were equal to the length of
     * this string: {@code -1} is returned.
     *
     * <p>All indices are specified in {@code char} values
     * (Unicode code units).
     *
     * @param   ch          a character (Unicode code point).
     * @param   fromIndex   the index to start the search from.
     * @return  the index of the first occurrence of the character in the
     *          character sequence represented by this object that is greater
     *          than or equal to {@code fromIndex}, or {@code -1}
     *          if the character does not occur.
     */
    public int indexOf(int ch, int fromIndex) {
        fromIndex = Math.max(0, fromIndex);
        if (isCompressed()) {
            int length = compressedLength();
            if (Integer.compareUnsigned(ch, Unicode.MAX_ASCII) > 0 || fromIndex >= length) {
                return -1;
            }
            long foundMask = -1L << fromIndex;
            return StringCompressed.indexOf(firstHalf(), secondHalf(), length, (byte)ch, foundMask);
        } else {
            int length = nonCompressedLength() - fromIndex;
            if (length <= 0) {
                return -1;
            }
            long address = address() + fromIndex * (long)Character.BYTES;
            return StringUTF16.indexOf(address, length, scope(), ch);
        }
    }

    /**
     * Returns the index within this string of the last occurrence of
     * the specified character. For values of {@code ch} in the
     * range from 0 to 0xFFFF (inclusive), the index (in Unicode code
     * units) returned is the largest value <i>k</i> such that:
     * <blockquote><pre>
     * this.charAt(<i>k</i>) == ch
     * </pre></blockquote>
     * is true. For other values of {@code ch}, it is the
     * largest value <i>k</i> such that:
     * <blockquote><pre>
     * this.codePointAt(<i>k</i>) == ch
     * </pre></blockquote>
     * is true.  In either case, if no such character occurs in this
     * string, then {@code -1} is returned.  The
     * {@code String} is searched backwards starting at the last
     * character.
     *
     * @param   ch   a character (Unicode code point).
     * @return  the index of the last occurrence of the character in the
     *          character sequence represented by this object, or
     *          {@code -1} if the character does not occur.
     */
    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, length() - 1);
    }

    /**
     * Returns the index within this string of the last occurrence of
     * the specified character, searching backward starting at the
     * specified index. For values of {@code ch} in the range
     * from 0 to 0xFFFF (inclusive), the index returned is the largest
     * value <i>k</i> such that:
     * <blockquote><pre>
     * (this.charAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &lt;= fromIndex)
     * </pre></blockquote>
     * is true. For other values of {@code ch}, it is the
     * largest value <i>k</i> such that:
     * <blockquote><pre>
     * (this.codePointAt(<i>k</i>) == ch) {@code &&} (<i>k</i> &lt;= fromIndex)
     * </pre></blockquote>
     * is true. In either case, if no such character occurs in this
     * string at or before position {@code fromIndex}, then
     * {@code -1} is returned.
     *
     * <p>All indices are specified in {@code char} values
     * (Unicode code units).
     *
     * @param   ch          a character (Unicode code point).
     * @param   fromIndex   the index to start the search from. There is no
     *          restriction on the value of {@code fromIndex}. If it is
     *          greater than or equal to the length of this string, it has
     *          the same effect as if it were equal to one less than the
     *          length of this string: this entire string may be searched.
     *          If it is negative, it has the same effect as if it were -1:
     *          -1 is returned.
     * @return  the index of the last occurrence of the character in the
     *          character sequence represented by this object that is less
     *          than or equal to {@code fromIndex}, or {@code -1}
     *          if the character does not occur before that point.
     */
    public int lastIndexOf(int ch, int fromIndex) {
        if (fromIndex < 0) {
            return -1;
        }
        if (isCompressed()) {
            if (Integer.compareUnsigned(ch, Unicode.MAX_ASCII) > 0) {
                return -1;
            }
            fromIndex = Math.min(fromIndex, compressedLength() - 1);
            long foundMask = -1L >>> (~fromIndex);
            return StringCompressed.lastIndexOf(firstHalf(), secondHalf(), (byte)ch, foundMask);
        } else {
            fromIndex = Math.min(fromIndex, nonCompressedLength() - 1);
            return StringUTF16.lastIndexOf(address(), fromIndex + 1, scope(), ch);
        }
    }

    /**
     * Returns the index within this string of the first occurrence of the
     * specified substring.
     *
     * <p>The returned index is the smallest value {@code k} for which:
     * <pre>{@code
     * this.startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str   the substring to search for.
     * @return  the index of the first occurrence of the specified substring,
     *          or {@code -1} if there is no such occurrence.
     */
    public int indexOf(InlineString str) {
        return indexOf(str, 0);
    }

    /**
     * Returns the index within this string of the first occurrence of the
     * specified substring, starting at the specified index.
     *
     * <p>The returned index is the smallest value {@code k} for which:
     * <pre>{@code
     *     k >= Math.min(fromIndex, this.length()) &&
     *                   this.startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str         the substring to search for.
     * @param   fromIndex   the index from which to start the search.
     * @return  the index of the first occurrence of the specified substring,
     *          starting at the specified index,
     *          or {@code -1} if there is no such occurrence.
     */
    public int indexOf(InlineString str, int fromIndex) {
        fromIndex = Math.max(0, fromIndex);
        if (str.isEmpty()) {
            return Math.min(fromIndex, length());
        }
        if (isCompressed()) {
            int length = compressedLength();
            if (fromIndex >= length) {
                return -1;
            }
            long foundMask = -1L << fromIndex;
            if (str.secondHalf == (1L << StringCompressed.LENGTH_SHIFT)) {
                return StringCompressed.indexOf(firstHalf(), secondHalf(),
                        length, (byte)str.firstHalf(), foundMask);
            }
            if (!str.isCompressed()) {
                return -1;
            }
            return StringCompressed.indexOf(firstHalf(), secondHalf(), length,
                    str.firstHalf(), str.secondHalf(), foundMask);
        } else {
            int length = nonCompressedLength() - fromIndex;
            if (length <= 0) {
                return -1;
            }
            long address = address() + fromIndex * (long)Character.BYTES;
            int subLength;
            short c0;
            short c1 = 0;
            if (str.isCompressed()) {
                subLength = str.compressedLength();
                c0 = (short)StringCompressed.charAt(str.firstHalf(), str.firstHalf(), 0);
                c1 = (short)StringCompressed.charAt(str.firstHalf(), str.firstHalf(), 1);
            } else {
                subLength = str.nonCompressedLength();
                c0 = (short)StringUTF16.charAt(str.address(), str.scope(), 0);
                if (subLength > 1) {
                    c1 = (short)StringUTF16.charAt(str.address(), str.scope(), 1);
                }
            }
            if (subLength == 1) {
                int index = StringUTF16Helper.indexOfBMP(address, length, scope(), c0);
                return index >= 0 ? index + fromIndex : -1;
            }
            int c1Bound = length - subLength + 2;
            if (c1Bound <= 1) {
                return -1;
            }
            int index = StringUTF16.indexOf(address, c1Bound, scope(), c0, c1,
                    str.firstHalf, str.secondHalf, str.scope, subLength);
            return index >= 0 ? index + fromIndex : -1;
        }
    }

    /**
     * Returns the index within this string of the last occurrence of the
     * specified substring.  The last occurrence of the empty string ""
     * is considered to occur at the index value {@code this.length()}.
     *
     * <p>The returned index is the largest value {@code k} for which:
     * <pre>{@code
     * this.startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str   the substring to search for.
     * @return  the index of the last occurrence of the specified substring,
     *          or {@code -1} if there is no such occurrence.
     */
    public int lastIndexOf(InlineString str) {
        return lastIndexOf(str, length());
    }

    /**
     * Returns the index within this string of the last occurrence of the
     * specified substring, searching backward starting at the specified index.
     *
     * <p>The returned index is the largest value {@code k} for which:
     * <pre>{@code
     *     k <= Math.min(fromIndex, this.length()) &&
     *                   this.startsWith(str, k)
     * }</pre>
     * If no such value of {@code k} exists, then {@code -1} is returned.
     *
     * @param   str         the substring to search for.
     * @param   fromIndex   the index to start the search from.
     * @return  the index of the last occurrence of the specified substring,
     *          searching backward from the specified index,
     *          or {@code -1} if there is no such occurrence.
     */
    public int lastIndexOf(InlineString str, int fromIndex) {
        int length = length();
        int subLength = str.length();
        fromIndex = Math.min(fromIndex, length - subLength);
        if (fromIndex < 0) {
            return -1;
        }
        if (subLength == 0) {
            return fromIndex;
        }
        int c0 = str.charAt(0);
        while (true) {
            fromIndex = lastIndexOf(c0, fromIndex);
            if (fromIndex == -1) {
                return -1;
            }
            if (regionMatches(fromIndex, str, 0, subLength)) {
                return fromIndex;
            }
        }
    }

    /**
     * Returns a string that is a substring of this string. The
     * substring begins with the character at the specified index and
     * extends to the end of this string. <p>
     * Examples:
     * <blockquote><pre>
     * "unhappy".substring(2) returns "happy"
     * "Harbison".substring(3) returns "bison"
     * "emptiness".substring(9) returns "" (an empty string)
     * </pre></blockquote>
     *
     * @param      beginIndex   the beginning index, inclusive.
     * @return     the specified substring.
     * @throws     IndexOutOfBoundsException  if
     *             {@code beginIndex} is negative or larger than the
     *             length of this {@code String} object.
     */
    public InlineString substring(int beginIndex) {
        return substring(beginIndex, DefaultAllocator.get());
    }

    public InlineString substring(int beginIndex, SegmentAllocator allocator) {
        if (isCompressed()) {
            int length = compressedLength();
            StringData.checkOffset(beginIndex, length);
            return new InlineString(StringCompressed.substringBegin(firstHalf(), secondHalf(), length, beginIndex));
        } else {
            int length = nonCompressedLength();
            StringData.checkOffset(beginIndex, length);
            int newLength = length - beginIndex;
            return new InlineString(StringUTF16.substring(address(), scope(),
                    beginIndex, newLength, allocator));
        }
    }

    public InlineString substringNoCopy(int beginIndex) {
        if (isCompressed()) {
            int length = compressedLength();
            StringData.checkOffset(beginIndex, length);
            return new InlineString(StringCompressed.substringBegin(firstHalf(), secondHalf(), length, beginIndex));
        } else {
            int length = nonCompressedLength();
            StringData.checkOffset(beginIndex, length);
            int newLength = length - beginIndex;
            return new InlineString(StringUTF16.substringNoCopy(address(), scope(),
                    beginIndex, newLength));
        }
    }

    /**
     * Returns a string that is a substring of this string. The
     * substring begins at the specified {@code beginIndex} and
     * extends to the character at index {@code endIndex - 1}.
     * Thus the length of the substring is {@code endIndex-beginIndex}.
     * <p>
     * Examples:
     * <blockquote><pre>
     * "hamburger".substring(4, 8) returns "urge"
     * "smiles".substring(1, 5) returns "mile"
     * </pre></blockquote>
     *
     * @param      beginIndex   the beginning index, inclusive.
     * @param      endIndex     the ending index, exclusive.
     * @return     the specified substring.
     * @throws     IndexOutOfBoundsException  if the
     *             {@code beginIndex} is negative, or
     *             {@code endIndex} is larger than the length of
     *             this {@code String} object, or
     *             {@code beginIndex} is larger than
     *             {@code endIndex}.
     */
    public InlineString substring(int beginIndex, int endIndex) {
        return substring(beginIndex, endIndex, DefaultAllocator.get());
    }

    public InlineString substring(int beginIndex, int endIndex, SegmentAllocator allocator) {
        if (isCompressed()) {
            int length = compressedLength();
            StringData.checkBoundsBeginEnd(beginIndex, endIndex, length);
            return new InlineString(StringCompressed.substringBeginEnd(firstHalf(), secondHalf(), beginIndex, endIndex));
        } else {
            int length = nonCompressedLength();
            StringData.checkOffset(beginIndex, length);
            int newLength = endIndex - beginIndex;
            return new InlineString(StringUTF16.substring(address(), scope(),
                    beginIndex, newLength, allocator));
        }
    }

    public InlineString substringNoCopy(int beginIndex, int endIndex) {
        if (isCompressed()) {
            int length = compressedLength();
            StringData.checkBoundsBeginEnd(beginIndex, endIndex, length);
            return new InlineString(StringCompressed.substringBeginEnd(firstHalf(), secondHalf(), beginIndex, endIndex));
        } else {
            int length = nonCompressedLength();
            StringData.checkOffset(beginIndex, length);
            int newLength = endIndex - beginIndex;
            return new InlineString(StringUTF16.substringNoCopy(address(), scope(),
                    beginIndex, newLength));
        }
    }

    /**
     * Returns a character sequence that is a subsequence of this sequence.
     *
     * <p> An invocation of this method of the form
     *
     * <blockquote><pre>
     * str.subSequence(begin,&nbsp;end)</pre></blockquote>
     *
     * behaves in exactly the same way as the invocation
     *
     * <blockquote><pre>
     * str.substring(begin,&nbsp;end)</pre></blockquote>
     *
     * @apiNote
     * This method is defined so that the {@code String} class can implement
     * the {@link CharSequence} interface.
     *
     * @param   beginIndex   the begin index, inclusive.
     * @param   endIndex     the end index, exclusive.
     * @return  the specified subsequence.
     *
     * @throws  IndexOutOfBoundsException
     *          if {@code beginIndex} or {@code endIndex} is negative,
     *          if {@code endIndex} is greater than {@code length()},
     *          or if {@code beginIndex} is greater than {@code endIndex}
     *
     * @since 1.4
     */
    @Override
    public InlineString subSequence(int beginIndex, int endIndex) {
        return this.substring(beginIndex, endIndex);
    }

    /**
     * Concatenates the specified string to the end of this string.
     * <p>
     * If the length of the argument string is {@code 0}, then this
     * {@code String} object is returned. Otherwise, a
     * {@code String} object is returned that represents a character
     * sequence that is the concatenation of the character sequence
     * represented by this {@code String} object and the character
     * sequence represented by the argument string.<p>
     * Examples:
     * <blockquote><pre>
     * "cares".concat("s") returns "caress"
     * "to".concat("get").concat("her") returns "together"
     * </pre></blockquote>
     *
     * @param   str   the {@code String} that is concatenated to the end
     *                of this {@code String}.
     * @return  a string that represents the concatenation of this object's
     *          characters followed by the string argument's characters.
     */
    public InlineString concat(InlineString str) {
        return concat(str, DefaultAllocator.get());
    }

    public InlineString concat(InlineString str, SegmentAllocator allocator) {
        if (StringData.COMPRESSED_STRINGS && (secondHalf | str.secondHalf) >= 0) {
            int length1 = compressedLength();
            int length = length1 + str.compressedLength();
            if (StringData.compressible(length)) {
                return new InlineString(StringCompressed.concat(firstHalf(), secondHalf(),
                        str.firstHalf(), str.secondHalf(), length1, length));
            }
        }

        int length1 = length();
        int length2 = str.length();
        if (length1 == 0) {
            return str;
        } else if (length2 == 0) {
            return this;
        }

        int length = length1 + length2;
        if (length < 0) {
            throw new OutOfMemoryError("Overflow: String length out of range");
        }

        var buffer = allocator.allocate(length * (long)Character.BYTES);
        long address = buffer.address();
        var scope = buffer.scope();
        var res = new StringData(address, -length, scope);
        if (isCompressed()) {
            StringUTF16Helper.copy(address, scope, firstHalf(), secondHalf(), length1);
        } else {
            StringUTF16Helper.copy(address, scope, address(), scope(), length1);
        }
        address += length1 * (long)Character.BYTES;
        if (str.isCompressed()) {
            StringUTF16Helper.copy(address, scope, str.firstHalf(), str.secondHalf(), length2);
        } else {
            StringUTF16Helper.copy(address, scope, str.address(), str.scope(), length2);
        }
        return new InlineString(res);
    }

    /**
     * Returns a string resulting from replacing all occurrences of
     * {@code oldChar} in this string with {@code newChar}.
     * <p>
     * If the character {@code oldChar} does not occur in the
     * character sequence represented by this {@code String} object,
     * then a reference to this {@code String} object is returned.
     * Otherwise, a {@code String} object is returned that
     * represents a character sequence identical to the character sequence
     * represented by this {@code String} object, except that every
     * occurrence of {@code oldChar} is replaced by an occurrence
     * of {@code newChar}.
     * <p>
     * Examples:
     * <blockquote><pre>
     * "mesquite in your cellar".replace('e', 'o')
     *         returns "mosquito in your collar"
     * "the war of baronets".replace('r', 'y')
     *         returns "the way of bayonets"
     * "sparring with a purple porpoise".replace('p', 't')
     *         returns "starring with a turtle tortoise"
     * "JonL".replace('q', 'x') returns "JonL" (no change)
     * </pre></blockquote>
     *
     * @param   oldChar   the old character.
     * @param   newChar   the new character.
     * @return  a string derived from this string by replacing every
     *          occurrence of {@code oldChar} with {@code newChar}.
     */
    public InlineString replace(char oldChar, char newChar) {
        return replace(oldChar, newChar, DefaultAllocator.get());
    }

    public InlineString replace(char oldChar, char newChar, SegmentAllocator allocator) {
        if (isCompressed()) {
            if (Integer.compareUnsigned(oldChar, Unicode.MAX_ASCII) > 0) {
                return this;
            }
            return new InlineString(StringReplace.replaceCompressed(firstHalf(), secondHalf(),
                    (byte)oldChar, (short)newChar, allocator));
        }

        return new InlineString(StringReplace.replaceUTF16(address(), nonCompressedLength(), scope(),
                (short)oldChar, (short)newChar, allocator));
    }

    /**
     * Tells whether or not this string matches the given <a
     * href="../util/regex/Pattern.html#sum">regular expression</a>.
     *
     * <p> An invocation of this method of the form
     * <i>str</i>{@code .matches(}<i>regex</i>{@code )} yields exactly the
     * same result as the expression
     *
     * <blockquote>
     * {@link java.util.regex.Pattern}.{@link java.util.regex.Pattern#matches(String,CharSequence)
     * matches(<i>regex</i>, <i>str</i>)}
     * </blockquote>
     *
     * @param   regex
     *          the regular expression to which this string is to be matched
     *
     * @return  {@code true} if, and only if, this string matches the
     *          given regular expression
     *
     * @throws  PatternSyntaxException
     *          if the regular expression's syntax is invalid
     *
     * @see java.util.regex.Pattern
     *
     * @since 1.4
     */
    public boolean matches(String regex) {
        return Pattern.matches(regex, this.toString());
    }

    /**
     * Returns true if and only if this string contains the specified
     * substring.
     *
     * @param s the string to search for
     * @return true if this string contains {@code s}, false otherwise
     * @since 1.5
     */
    public boolean contains(InlineString s) {
        return indexOf(s) >= 0;
    }

    /**
     * Returns true if and only if this string contains the specified
     * sequence of char values.
     *
     * @param s the sequence to search for
     * @return true if this string contains {@code s}, false otherwise
     * @since 1.5
     */
    public boolean contains(CharSequence s) {
        return contains(new InlineString(s.toString()));
    }

    /**
     * Replaces the first substring of this string that matches the given <a
     * href="../util/regex/Pattern.html#sum">regular expression</a> with the
     * given replacement.
     *
     * <p> An invocation of this method of the form
     * <i>str</i>{@code .replaceFirst(}<i>regex</i>{@code ,} <i>repl</i>{@code )}
     * yields exactly the same result as the expression
     *
     * <blockquote>
     * <code>
     * {@link java.util.regex.Pattern}.{@link
     * java.util.regex.Pattern#compile(String) compile}(<i>regex</i>).{@link
     * java.util.regex.Pattern#matcher(java.lang.CharSequence) matcher}(<i>str</i>).{@link
     * java.util.regex.Matcher#replaceFirst(String) replaceFirst}(<i>repl</i>)
     * </code>
     * </blockquote>
     *
     *<p>
     * Note that backslashes ({@code \}) and dollar signs ({@code $}) in the
     * replacement string may cause the results to be different than if it were
     * being treated as a literal replacement string; see
     * {@link java.util.regex.Matcher#replaceFirst}.
     * Use {@link java.util.regex.Matcher#quoteReplacement} to suppress the special
     * meaning of these characters, if desired.
     *
     * @param   regex
     *          the regular expression to which this string is to be matched
     * @param   replacement
     *          the string to be substituted for the first match
     *
     * @return  The resulting {@code String}
     *
     * @throws  PatternSyntaxException
     *          if the regular expression's syntax is invalid
     *
     * @see java.util.regex.Pattern
     *
     * @since 1.4
     */
    public InlineString replaceFirst(String regex, String replacement) {
        return new InlineString(Pattern.compile(regex).matcher(this.toString()).replaceFirst(replacement));
    }

    /**
     * Replaces each substring of this string that matches the given <a
     * href="../util/regex/Pattern.html#sum">regular expression</a> with the
     * given replacement.
     *
     * <p> An invocation of this method of the form
     * <i>str</i>{@code .replaceAll(}<i>regex</i>{@code ,} <i>repl</i>{@code )}
     * yields exactly the same result as the expression
     *
     * <blockquote>
     * <code>
     * {@link java.util.regex.Pattern}.{@link
     * java.util.regex.Pattern#compile(String) compile}(<i>regex</i>).{@link
     * java.util.regex.Pattern#matcher(java.lang.CharSequence) matcher}(<i>str</i>).{@link
     * java.util.regex.Matcher#replaceAll(String) replaceAll}(<i>repl</i>)
     * </code>
     * </blockquote>
     *
     *<p>
     * Note that backslashes ({@code \}) and dollar signs ({@code $}) in the
     * replacement string may cause the results to be different than if it were
     * being treated as a literal replacement string; see
     * {@link java.util.regex.Matcher#replaceAll Matcher.replaceAll}.
     * Use {@link java.util.regex.Matcher#quoteReplacement} to suppress the special
     * meaning of these characters, if desired.
     *
     * @param   regex
     *          the regular expression to which this string is to be matched
     * @param   replacement
     *          the string to be substituted for each match
     *
     * @return  The resulting {@code String}
     *
     * @throws  PatternSyntaxException
     *          if the regular expression's syntax is invalid
     *
     * @see java.util.regex.Pattern
     *
     * @since 1.4
     */
    public InlineString replaceAll(String regex, String replacement) {
        return new InlineString(Pattern.compile(regex).matcher(this.toString()).replaceAll(replacement));
    }

    /**
     * Replaces each substring of this string that matches the literal target
     * sequence with the specified literal replacement sequence. The
     * replacement proceeds from the beginning of the string to the end, for
     * example, replacing "aa" with "b" in the string "aaa" will result in
     * "ba" rather than "ab".
     *
     * @param  target The sequence of char values to be replaced
     * @param  replacement The replacement sequence of char values
     * @return  The resulting string
     * @since 1.5
     */
    public InlineString replace(InlineString oldStr, InlineString newStr, SegmentAllocator allocator) {
        int thisLen = length();
        int oldStrLen = oldStr.length();
        int newStrLen = newStr.length();

        if (oldStrLen == 1 && newStrLen == 1) {
            return replace(oldStr.charAt(0), newStr.charAt(0), allocator);
        } else if (oldStrLen == 0) {

        }

        if (trgtLen > 0) {
            if (trgtLen == 1 && replLen == 1) {
                return replace(trgtStr.charAt(0), replStr.charAt(0));
            }

            boolean thisIsLatin1 = this.isLatin1();
            boolean trgtIsLatin1 = trgtStr.isLatin1();
            boolean replIsLatin1 = replStr.isLatin1();
            return (thisIsLatin1 && trgtIsLatin1 && replIsLatin1)
                    ? StringLatin1.replace(content(), thisLen,
                    trgtStr.content(), trgtLen,
                    replStr.content(), replLen)
                    : StringUTF16.replace(content(), thisLen, thisIsLatin1,
                    trgtStr.content(), trgtLen, trgtIsLatin1,
                    replStr.content(), replLen, replIsLatin1);
        } else { // trgtLen == 0
            long resultLen = thisLen + (thisLen + 1L) * replLen;
            if (resultLen > Integer.MAX_VALUE) {
                throw new OutOfMemoryError("Required index exceeds implementation limit");
            }

            var sb = new StringBuilder((int)resultLen).append(replStr.toString());
            this.chars().forEach(c -> sb.append((char)c).append(replStr.toString()));
            return new InlineString(sb);
        }
    }

    /**
     * Replaces each substring of this string that matches the literal target
     * sequence with the specified literal replacement sequence. The
     * replacement proceeds from the beginning of the string to the end, for
     * example, replacing "aa" with "b" in the string "aaa" will result in
     * "ba" rather than "ab".
     *
     * @param  target The sequence of char values to be replaced
     * @param  replacement The replacement sequence of char values
     * @return  The resulting string
     * @since 1.5
     */
    public InlineString replace(CharSequence target, CharSequence replacement) {
        return this.replace(new InlineString(target.toString()), new InlineString(replacement.toString()));
    }

    /**
     * Splits this string around matches of the given
     * <a href="../util/regex/Pattern.html#sum">regular expression</a>.
     *
     * <p> The array returned by this method contains each substring of this
     * string that is terminated by another substring that matches the given
     * expression or is terminated by the end of the string.  The substrings in
     * the array are in the order in which they occur in this string.  If the
     * expression does not match any part of the input then the resulting array
     * has just one element, namely this string.
     *
     * <p> When there is a positive-width match at the beginning of this
     * string then an empty leading substring is included at the beginning
     * of the resulting array. A zero-width match at the beginning however
     * never produces such empty leading substring.
     *
     * <p> The {@code limit} parameter controls the number of times the
     * pattern is applied and therefore affects the length of the resulting
     * array.
     * <ul>
     *    <li><p>
     *    If the <i>limit</i> is positive then the pattern will be applied
     *    at most <i>limit</i>&nbsp;-&nbsp;1 times, the array's length will be
     *    no greater than <i>limit</i>, and the array's last entry will contain
     *    all input beyond the last matched delimiter.</p></li>
     *
     *    <li><p>
     *    If the <i>limit</i> is zero then the pattern will be applied as
     *    many times as possible, the array can have any length, and trailing
     *    empty strings will be discarded.</p></li>
     *
     *    <li><p>
     *    If the <i>limit</i> is negative then the pattern will be applied
     *    as many times as possible and the array can have any length.</p></li>
     * </ul>
     *
     * <p> The string {@code "boo:and:foo"}, for example, yields the
     * following results with these parameters:
     *
     * <blockquote><table class="plain">
     * <caption style="display:none">Split example showing regex, limit, and result</caption>
     * <thead>
     * <tr>
     *     <th scope="col">Regex</th>
     *     <th scope="col">Limit</th>
     *     <th scope="col">Result</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row" rowspan="3" style="font-weight:normal">:</th>
     *     <th scope="row" style="font-weight:normal; text-align:right; padding-right:1em">2</th>
     *     <td>{@code { "boo", "and:foo" }}</td></tr>
     * <tr><!-- : -->
     *     <th scope="row" style="font-weight:normal; text-align:right; padding-right:1em">5</th>
     *     <td>{@code { "boo", "and", "foo" }}</td></tr>
     * <tr><!-- : -->
     *     <th scope="row" style="font-weight:normal; text-align:right; padding-right:1em">-2</th>
     *     <td>{@code { "boo", "and", "foo" }}</td></tr>
     * <tr><th scope="row" rowspan="3" style="font-weight:normal">o</th>
     *     <th scope="row" style="font-weight:normal; text-align:right; padding-right:1em">5</th>
     *     <td>{@code { "b", "", ":and:f", "", "" }}</td></tr>
     * <tr><!-- o -->
     *     <th scope="row" style="font-weight:normal; text-align:right; padding-right:1em">-2</th>
     *     <td>{@code { "b", "", ":and:f", "", "" }}</td></tr>
     * <tr><!-- o -->
     *     <th scope="row" style="font-weight:normal; text-align:right; padding-right:1em">0</th>
     *     <td>{@code { "b", "", ":and:f" }}</td></tr>
     * </tbody>
     * </table></blockquote>
     *
     * <p> An invocation of this method of the form
     * <i>str.</i>{@code split(}<i>regex</i>{@code ,}&nbsp;<i>n</i>{@code )}
     * yields the same result as the expression
     *
     * <blockquote>
     * <code>
     * {@link java.util.regex.Pattern}.{@link
     * java.util.regex.Pattern#compile(String) compile}(<i>regex</i>).{@link
     * java.util.regex.Pattern#split(java.lang.CharSequence,int) split}(<i>str</i>,&nbsp;<i>n</i>)
     * </code>
     * </blockquote>
     *
     *
     * @param  regex
     *         the delimiting regular expression
     *
     * @param  limit
     *         the result threshold, as described above
     *
     * @return  the array of strings computed by splitting this string
     *          around matches of the given regular expression
     *
     * @throws  PatternSyntaxException
     *          if the regular expression's syntax is invalid
     *
     * @see java.util.regex.Pattern
     *
     * @since 1.4
     */
    public InlineString[] split(String regex, int limit) {
        /* fastpath if the regex is a
         * (1) one-char String and this character is not one of the
         *     RegEx's meta characters ".$|()[{^?*+\\", or
         * (2) two-char String and the first char is the backslash and
         *     the second is not the ascii digit or ascii letter.
         */
        char ch;
        if (((regex.length() == 1 &&
                                ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) ||
                        (regex.length() == 2 &&
                                regex.charAt(0) == '\\' &&
                                (((ch = regex.charAt(1)) - '0') | ('9' - ch)) < 0 &&
                                ((ch - 'a') | ('z' - ch)) < 0 &&
                                ((ch - 'A') | ('Z' - ch)) < 0)) &&
                (ch < Character.MIN_HIGH_SURROGATE ||
                        ch > Character.MAX_LOW_SURROGATE))
        {
            int off = 0;
            int next = 0;
            boolean limited = limit > 0;
            int i = 0;
            InlineString[] list = new InlineString[10];
            while ((next = indexOf(ch, off)) != -1) {
                if (!limited || i < limit - 1) {
                    if (i == list.length) {
                        var newList = new InlineString[i << 1];
                        System.arraycopy(list, 0, newList, 0, i);
                        list = newList;
                    }
                    list[i++] = substring(off, next);
                    off = next + 1;
                } else {    // last one
                    //assert (list.size() == limit - 1);
                    int last = length();
                    if (i == list.length) {
                        var newList = new InlineString[i + 1];
                        System.arraycopy(list, 0, newList, 0, i);
                        list = newList;
                    }
                    list[i++] = substring(off, last);
                    off = last;
                    break;
                }
            }
            // If no match was found, return this
            if (off == 0)
                return new InlineString[]{this};

            // Add remaining segment
            if (!limited || i < limit) {
                if (i == list.length) {
                    var newList = new InlineString[i + 1];
                    System.arraycopy(list, 0, newList, 0, i);
                    list = newList;
                }
                list[i++] = substring(off, length());
            }

            // Construct result
            int resultSize = i;
            if (limit == 0) {
                while (resultSize > 0 && list[resultSize - 1].isEmpty()) {
                    resultSize--;
                }
            }
            if (resultSize == list.length) {
                return list;
            } else {
                var newList = new InlineString[resultSize];
                System.arraycopy(list, 0, newList, 0, resultSize);
                return newList;
            }
        } else {
            var temp = Pattern.compile(regex).split(this, limit);
            var result = new InlineString[temp.length];
            for (int i = 0; i < temp.length; i++) {
                result[i] = new InlineString(temp[i]);
            }
            return result;
        }
    }

    /**
     * Splits this string around matches of the given <a
     * href="../util/regex/Pattern.html#sum">regular expression</a>.
     *
     * <p> This method works as if by invoking the two-argument {@link
     * #split(String, int) split} method with the given expression and a limit
     * argument of zero.  Trailing empty strings are therefore not included in
     * the resulting array.
     *
     * <p> The string {@code "boo:and:foo"}, for example, yields the following
     * results with these expressions:
     *
     * <blockquote><table class="plain">
     * <caption style="display:none">Split examples showing regex and result</caption>
     * <thead>
     * <tr>
     *  <th scope="col">Regex</th>
     *  <th scope="col">Result</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr><th scope="row" style="text-weight:normal">:</th>
     *     <td>{@code { "boo", "and", "foo" }}</td></tr>
     * <tr><th scope="row" style="text-weight:normal">o</th>
     *     <td>{@code { "b", "", ":and:f" }}</td></tr>
     * </tbody>
     * </table></blockquote>
     *
     *
     * @param  regex
     *         the delimiting regular expression
     *
     * @return  the array of strings computed by splitting this string
     *          around matches of the given regular expression
     *
     * @throws  PatternSyntaxException
     *          if the regular expression's syntax is invalid
     *
     * @see java.util.regex.Pattern
     *
     * @since 1.4
     */
    public InlineString[] split(String regex) {
        return split(regex, 0);
    }

    /**
     * Returns a new String composed of copies of the
     * {@code CharSequence elements} joined together with a copy of
     * the specified {@code delimiter}.
     *
     * <blockquote>For example,
     * <pre>{@code
     *     String message = String.join("-", "Java", "is", "cool");
     *     // message returned is: "Java-is-cool"
     * }</pre></blockquote>
     *
     * Note that if an element is null, then {@code "null"} is added.
     *
     * @param  delimiter the delimiter that separates each element
     * @param  elements the elements to join together.
     *
     * @return a new {@code String} that is composed of the {@code elements}
     *         separated by the {@code delimiter}
     *
     * @throws NullPointerException If {@code delimiter} or {@code elements}
     *         is {@code null}
     *
     * @see java.util.StringJoiner
     * @since 1.8
     */
    public static InlineString join(CharSequence delimiter, CharSequence... elements) {
        return new InlineString(String.join(delimiter, elements));
    }

    /**
     * Returns a new {@code String} composed of copies of the
     * {@code CharSequence elements} joined together with a copy of the
     * specified {@code delimiter}.
     *
     * <blockquote>For example,
     * <pre>{@code
     *     List<String> strings = List.of("Java", "is", "cool");
     *     String message = String.join(" ", strings);
     *     // message returned is: "Java is cool"
     *
     *     Set<String> strings =
     *         new LinkedHashSet<>(List.of("Java", "is", "very", "cool"));
     *     String message = String.join("-", strings);
     *     // message returned is: "Java-is-very-cool"
     * }</pre></blockquote>
     *
     * Note that if an individual element is {@code null}, then {@code "null"} is added.
     *
     * @param  delimiter a sequence of characters that is used to separate each
     *         of the {@code elements} in the resulting {@code String}
     * @param  elements an {@code Iterable} that will have its {@code elements}
     *         joined together.
     *
     * @return a new {@code String} that is composed from the {@code elements}
     *         argument
     *
     * @throws NullPointerException If {@code delimiter} or {@code elements}
     *         is {@code null}
     *
     * @see    #join(CharSequence,CharSequence...)
     * @see    java.util.StringJoiner
     * @since 1.8
     */
    public static InlineString join(CharSequence delimiter,
                                    Iterable<? extends CharSequence> elements) {
        return new InlineString(String.join(delimiter, elements));
    }

    /**
     * Converts all of the characters in this {@code String} to lower
     * case using the rules of the given {@code Locale}.  Case mapping is based
     * on the Unicode Standard version specified by the {@link java.lang.Character Character}
     * class. Since case mappings are not always 1:1 char mappings, the resulting
     * {@code String} may be a different length than the original {@code String}.
     * <p>
     * Examples of lowercase  mappings are in the following table:
     * <table class="plain">
     * <caption style="display:none">Lowercase mapping examples showing language code of locale, upper case, lower case, and description</caption>
     * <thead>
     * <tr>
     *   <th scope="col">Language Code of Locale</th>
     *   <th scope="col">Upper Case</th>
     *   <th scope="col">Lower Case</th>
     *   <th scope="col">Description</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>tr (Turkish)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">&#92;u0130</th>
     *   <td>&#92;u0069</td>
     *   <td>capital letter I with dot above -&gt; small letter i</td>
     * </tr>
     * <tr>
     *   <td>tr (Turkish)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">&#92;u0049</th>
     *   <td>&#92;u0131</td>
     *   <td>capital letter I -&gt; small letter dotless i </td>
     * </tr>
     * <tr>
     *   <td>(all)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">French Fries</th>
     *   <td>french fries</td>
     *   <td>lowercased all chars in String</td>
     * </tr>
     * <tr>
     *   <td>(all)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">
     *       &Iota;&Chi;&Theta;&Upsilon;&Sigma;</th>
     *   <td>&iota;&chi;&theta;&upsilon;&sigma;</td>
     *   <td>lowercased all chars in String</td>
     * </tr>
     * </tbody>
     * </table>
     *
     * @param locale use the case transformation rules for this locale
     * @return the {@code String}, converted to lowercase.
     * @see     java.lang.String#toLowerCase()
     * @see     java.lang.String#toUpperCase()
     * @see     java.lang.String#toUpperCase(Locale)
     * @since   1.1
     */
    public InlineString toLowerCase(Locale locale) {
        return isLatin1() ? StringLatin1.toLowerCase(this, content(), locale)
                : StringUTF16.toLowerCase(this, content(), locale);
    }

    /**
     * Converts all of the characters in this {@code String} to lower
     * case using the rules of the default locale. This is equivalent to calling
     * {@code toLowerCase(Locale.getDefault())}.
     * <p>
     * <b>Note:</b> This method is locale sensitive, and may produce unexpected
     * results if used for strings that are intended to be interpreted locale
     * independently.
     * Examples are programming language identifiers, protocol keys, and HTML
     * tags.
     * For instance, {@code "TITLE".toLowerCase()} in a Turkish locale
     * returns {@code "t\u005Cu0131tle"}, where '\u005Cu0131' is the
     * LATIN SMALL LETTER DOTLESS I character.
     * To obtain correct results for locale insensitive strings, use
     * {@code toLowerCase(Locale.ROOT)}.
     *
     * @return  the {@code String}, converted to lowercase.
     * @see     java.lang.String#toLowerCase(Locale)
     */
    public InlineString toLowerCase() {
        return toLowerCase(Locale.getDefault());
    }

    /**
     * Converts all of the characters in this {@code String} to upper
     * case using the rules of the given {@code Locale}. Case mapping is based
     * on the Unicode Standard version specified by the {@link java.lang.Character Character}
     * class. Since case mappings are not always 1:1 char mappings, the resulting
     * {@code String} may be a different length than the original {@code String}.
     * <p>
     * Examples of locale-sensitive and 1:M case mappings are in the following table.
     *
     * <table class="plain">
     * <caption style="display:none">Examples of locale-sensitive and 1:M case mappings. Shows Language code of locale, lower case, upper case, and description.</caption>
     * <thead>
     * <tr>
     *   <th scope="col">Language Code of Locale</th>
     *   <th scope="col">Lower Case</th>
     *   <th scope="col">Upper Case</th>
     *   <th scope="col">Description</th>
     * </tr>
     * </thead>
     * <tbody>
     * <tr>
     *   <td>tr (Turkish)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">&#92;u0069</th>
     *   <td>&#92;u0130</td>
     *   <td>small letter i -&gt; capital letter I with dot above</td>
     * </tr>
     * <tr>
     *   <td>tr (Turkish)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">&#92;u0131</th>
     *   <td>&#92;u0049</td>
     *   <td>small letter dotless i -&gt; capital letter I</td>
     * </tr>
     * <tr>
     *   <td>(all)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">&#92;u00df</th>
     *   <td>&#92;u0053 &#92;u0053</td>
     *   <td>small letter sharp s -&gt; two letters: SS</td>
     * </tr>
     * <tr>
     *   <td>(all)</td>
     *   <th scope="row" style="font-weight:normal; text-align:left">Fahrvergn&uuml;gen</th>
     *   <td>FAHRVERGN&Uuml;GEN</td>
     *   <td></td>
     * </tr>
     * </tbody>
     * </table>
     * @param locale use the case transformation rules for this locale
     * @return the {@code String}, converted to uppercase.
     * @see     java.lang.String#toUpperCase()
     * @see     java.lang.String#toLowerCase()
     * @see     java.lang.String#toLowerCase(Locale)
     * @since   1.1
     */
    public InlineString toUpperCase(Locale locale) {
        return isLatin1() ? StringLatin1.toUpperCase(this, content(), locale)
                : StringUTF16.toUpperCase(this, content(), locale);
    }

    /**
     * Converts all of the characters in this {@code String} to upper
     * case using the rules of the default locale. This method is equivalent to
     * {@code toUpperCase(Locale.getDefault())}.
     * <p>
     * <b>Note:</b> This method is locale sensitive, and may produce unexpected
     * results if used for strings that are intended to be interpreted locale
     * independently.
     * Examples are programming language identifiers, protocol keys, and HTML
     * tags.
     * For instance, {@code "title".toUpperCase()} in a Turkish locale
     * returns {@code "T\u005Cu0130TLE"}, where '\u005Cu0130' is the
     * LATIN CAPITAL LETTER I WITH DOT ABOVE character.
     * To obtain correct results for locale insensitive strings, use
     * {@code toUpperCase(Locale.ROOT)}.
     *
     * @return  the {@code String}, converted to uppercase.
     * @see     java.lang.String#toUpperCase(Locale)
     */
    public InlineString toUpperCase() {
        return toUpperCase(Locale.getDefault());
    }

    /**
     * Returns a string whose value is this string, with all leading
     * and trailing space removed, where space is defined
     * as any character whose codepoint is less than or equal to
     * {@code 'U+0020'} (the space character).
     * <p>
     * If this {@code String} object represents an empty character
     * sequence, or the first and last characters of character sequence
     * represented by this {@code String} object both have codes
     * that are not space (as defined above), then a
     * reference to this {@code String} object is returned.
     * <p>
     * Otherwise, if all characters in this string are space (as
     * defined above), then a  {@code String} object representing an
     * empty string is returned.
     * <p>
     * Otherwise, let <i>k</i> be the index of the first character in the
     * string whose code is not a space (as defined above) and let
     * <i>m</i> be the index of the last character in the string whose code
     * is not a space (as defined above). A {@code String}
     * object is returned, representing the substring of this string that
     * begins with the character at index <i>k</i> and ends with the
     * character at index <i>m</i>-that is, the result of
     * {@code this.substring(k, m + 1)}.
     * <p>
     * This method may be used to trim space (as defined above) from
     * the beginning and end of a string.
     *
     * @return  a string whose value is this string, with all leading
     *          and trailing space removed, or this string if it
     *          has no leading or trailing space.
     */
    public InlineString trim() {
        if (isCompressed()) {
            return StringCompressed.trim(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            return StringLatin1.trim(value);
        } else {
            return StringUTF16.trim(value);
        }
    }

    /**
     * Returns a string whose value is this string, with all leading
     * and trailing {@linkplain Character#isWhitespace(int) white space}
     * removed.
     * <p>
     * If this {@code String} object represents an empty string,
     * or if all code points in this string are
     * {@linkplain Character#isWhitespace(int) white space}, then an empty string
     * is returned.
     * <p>
     * Otherwise, returns a substring of this string beginning with the first
     * code point that is not a {@linkplain Character#isWhitespace(int) white space}
     * up to and including the last code point that is not a
     * {@linkplain Character#isWhitespace(int) white space}.
     * <p>
     * This method may be used to strip
     * {@linkplain Character#isWhitespace(int) white space} from
     * the beginning and end of a string.
     *
     * @return  a string whose value is this string, with all leading
     *          and trailing white space removed
     *
     * @see Character#isWhitespace(int)
     *
     * @since 11
     */
    public InlineString strip() {
        if (isCompressed()) {
            return StringCompressed.strip(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            return StringLatin1.strip(value);
        } else {
            return StringUTF16.strip(value);
        }
    }

    /**
     * Returns a string whose value is this string, with all leading
     * {@linkplain Character#isWhitespace(int) white space} removed.
     * <p>
     * If this {@code String} object represents an empty string,
     * or if all code points in this string are
     * {@linkplain Character#isWhitespace(int) white space}, then an empty string
     * is returned.
     * <p>
     * Otherwise, returns a substring of this string beginning with the first
     * code point that is not a {@linkplain Character#isWhitespace(int) white space}
     * up to and including the last code point of this string.
     * <p>
     * This method may be used to trim
     * {@linkplain Character#isWhitespace(int) white space} from
     * the beginning of a string.
     *
     * @return  a string whose value is this string, with all leading white
     *          space removed
     *
     * @see Character#isWhitespace(int)
     *
     * @since 11
     */
    public InlineString stripLeading() {
        if (isCompressed()) {
            return StringCompressed.stripLeading(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            return StringLatin1.stripLeading(value);
        } else {
            return StringUTF16.stripLeading(value);
        }
    }

    /**
     * Returns a string whose value is this string, with all trailing
     * {@linkplain Character#isWhitespace(int) white space} removed.
     * <p>
     * If this {@code String} object represents an empty string,
     * or if all characters in this string are
     * {@linkplain Character#isWhitespace(int) white space}, then an empty string
     * is returned.
     * <p>
     * Otherwise, returns a substring of this string beginning with the first
     * code point of this string up to and including the last code point
     * that is not a {@linkplain Character#isWhitespace(int) white space}.
     * <p>
     * This method may be used to trim
     * {@linkplain Character#isWhitespace(int) white space} from
     * the end of a string.
     *
     * @return  a string whose value is this string, with all trailing white
     *          space removed
     *
     * @see Character#isWhitespace(int)
     *
     * @since 11
     */
    public InlineString stripTrailing() {
        if (isCompressed()) {
            return StringCompressed.stripTrailing(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            return StringLatin1.stripTrailing(value);
        } else {
            return StringUTF16.stripTrailing(value);
        }
    }

    /**
     * Returns {@code true} if the string is empty or contains only
     * {@linkplain Character#isWhitespace(int) white space} codepoints,
     * otherwise {@code false}.
     *
     * @return {@code true} if the string is empty or contains only
     *         {@linkplain Character#isWhitespace(int) white space} codepoints,
     *         otherwise {@code false}
     *
     * @see Character#isWhitespace(int)
     *
     * @since 11
     */
    public boolean isBlank() {
        return indexOfNonWhitespace() == length();
    }

    /**
     * Returns a stream of lines extracted from this string,
     * separated by line terminators.
     * <p>
     * A <i>line terminator</i> is one of the following:
     * a line feed character {@code "\n"} (U+000A),
     * a carriage return character {@code "\r"} (U+000D),
     * or a carriage return followed immediately by a line feed
     * {@code "\r\n"} (U+000D U+000A).
     * <p>
     * A <i>line</i> is either a sequence of zero or more characters
     * followed by a line terminator, or it is a sequence of one or
     * more characters followed by the end of the string. A
     * line does not include the line terminator.
     * <p>
     * The stream returned by this method contains the lines from
     * this string in the order in which they occur.
     *
     * @apiNote This definition of <i>line</i> implies that an empty
     *          string has zero lines and that there is no empty line
     *          following a line terminator at the end of a string.
     *
     * @implNote This method provides better performance than
     *           split("\R") by supplying elements lazily and
     *           by faster search of new line terminators.
     *
     * @return  the stream of lines extracted from this string
     *
     * @since 11
     */
    public Stream<InlineString.ref> lines() {
        if (isCompressed()) {
            return StringCompressed.lines(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            return StringLatin1.lines(value);
        } else {
            return StringUTF16.lines(value);
        }
    }

    /**
     * Adjusts the indentation of each line of this string based on the value of
     * {@code n}, and normalizes line termination characters.
     * <p>
     * This string is conceptually separated into lines using
     * {@link String#lines()}. Each line is then adjusted as described below
     * and then suffixed with a line feed {@code "\n"} (U+000A). The resulting
     * lines are then concatenated and returned.
     * <p>
     * If {@code n > 0} then {@code n} spaces (U+0020) are inserted at the
     * beginning of each line.
     * <p>
     * If {@code n < 0} then up to {@code n}
     * {@linkplain Character#isWhitespace(int) white space characters} are removed
     * from the beginning of each line. If a given line does not contain
     * sufficient white space then all leading
     * {@linkplain Character#isWhitespace(int) white space characters} are removed.
     * Each white space character is treated as a single character. In
     * particular, the tab character {@code "\t"} (U+0009) is considered a
     * single character; it is not expanded.
     * <p>
     * If {@code n == 0} then the line remains unchanged. However, line
     * terminators are still normalized.
     *
     * @param n  number of leading
     *           {@linkplain Character#isWhitespace(int) white space characters}
     *           to add or remove
     *
     * @return string with indentation adjusted and line endings normalized
     *
     * @see String#lines()
     * @see String#isBlank()
     * @see Character#isWhitespace(int)
     *
     * @since 12
     */
    public InlineString indent(int n) {
        if (isEmpty()) {
            return EMPTY_STRING;
        }
        Stream<InlineString.ref> stream = lines();
        if (n > 0) {
            final var spaces = valueOf(' ').repeat(n);
            stream = stream.map(s -> spaces.concat(s));
        } else if (n == Integer.MIN_VALUE) {
            stream = stream.map(s -> s.stripLeading());
        } else if (n < 0) {
            stream = stream.map(s -> s.substring(Math.min(-n, s.indexOfNonWhitespace())));
        }
        return stream.collect(Collector.of(StringBuilder::new,
                (sb, ele) -> sb.append(ele.toString()).append('\n'),
                StringBuilder::append,
                InlineString::new,
                Collector.Characteristics.CONCURRENT));
    }

    /**
     * Returns a string whose value is this string, with incidental
     * {@linkplain Character#isWhitespace(int) white space} removed from
     * the beginning and end of every line.
     * <p>
     * Incidental {@linkplain Character#isWhitespace(int) white space}
     * is often present in a text block to align the content with the opening
     * delimiter. For example, in the following code, dots represent incidental
     * {@linkplain Character#isWhitespace(int) white space}:
     * <blockquote><pre>
     * String html = """
     * ..............&lt;html&gt;
     * ..............    &lt;body&gt;
     * ..............        &lt;p&gt;Hello, world&lt;/p&gt;
     * ..............    &lt;/body&gt;
     * ..............&lt;/html&gt;
     * ..............""";
     * </pre></blockquote>
     * This method treats the incidental
     * {@linkplain Character#isWhitespace(int) white space} as indentation to be
     * stripped, producing a string that preserves the relative indentation of
     * the content. Using | to visualize the start of each line of the string:
     * <blockquote><pre>
     * |&lt;html&gt;
     * |    &lt;body&gt;
     * |        &lt;p&gt;Hello, world&lt;/p&gt;
     * |    &lt;/body&gt;
     * |&lt;/html&gt;
     * </pre></blockquote>
     * First, the individual lines of this string are extracted. A <i>line</i>
     * is a sequence of zero or more characters followed by either a line
     * terminator or the end of the string.
     * If the string has at least one line terminator, the last line consists
     * of the characters between the last terminator and the end of the string.
     * Otherwise, if the string has no terminators, the last line is the start
     * of the string to the end of the string, in other words, the entire
     * string.
     * A line does not include the line terminator.
     * <p>
     * Then, the <i>minimum indentation</i> (min) is determined as follows:
     * <ul>
     *   <li><p>For each non-blank line (as defined by {@link String#isBlank()}),
     *   the leading {@linkplain Character#isWhitespace(int) white space}
     *   characters are counted.</p>
     *   </li>
     *   <li><p>The leading {@linkplain Character#isWhitespace(int) white space}
     *   characters on the last line are also counted even if
     *   {@linkplain String#isBlank() blank}.</p>
     *   </li>
     * </ul>
     * <p>The <i>min</i> value is the smallest of these counts.
     * <p>
     * For each {@linkplain String#isBlank() non-blank} line, <i>min</i> leading
     * {@linkplain Character#isWhitespace(int) white space} characters are
     * removed, and any trailing {@linkplain Character#isWhitespace(int) white
     * space} characters are removed. {@linkplain String#isBlank() Blank} lines
     * are replaced with the empty string.
     *
     * <p>
     * Finally, the lines are joined into a new string, using the LF character
     * {@code "\n"} (U+000A) to separate lines.
     *
     * @apiNote
     * This method's primary purpose is to shift a block of lines as far as
     * possible to the left, while preserving relative indentation. Lines
     * that were indented the least will thus have no leading
     * {@linkplain Character#isWhitespace(int) white space}.
     * The result will have the same number of line terminators as this string.
     * If this string ends with a line terminator then the result will end
     * with a line terminator.
     *
     * @implSpec
     * This method treats all {@linkplain Character#isWhitespace(int) white space}
     * characters as having equal width. As long as the indentation on every
     * line is consistently composed of the same character sequences, then the
     * result will be as described above.
     *
     * @return string with incidental indentation removed and line
     *         terminators normalized
     *
     * @see String#lines()
     * @see String#isBlank()
     * @see String#indent(int)
     * @see Character#isWhitespace(int)
     *
     * @since 15
     *
     */
    public InlineString stripIndent() {
        int length = length();
        if (length == 0) {
            return EMPTY_STRING;
        }
        char lastChar = charAt(length - 1);
        boolean optOut = lastChar == '\n' || lastChar == '\r';
        var lines = lines().toList();
        final int outdent = optOut ? 0 : outdent(lines);
        return lines.stream().map(line -> {
                    int firstNonWhitespace = line.indexOfNonWhitespace();
                    int lastNonWhitespace = line.lastIndexOfNonWhitespace();
                    int incidentalWhitespace = Math.min(outdent, firstNonWhitespace);
                    return firstNonWhitespace > lastNonWhitespace
                            ? "" : line.substring(incidentalWhitespace, lastNonWhitespace);
                })
                .collect(Collector.of(StringBuilder::new,
                        (sb, ele) -> sb.append(ele.toString()).append('\n'),
                        StringBuilder::append,
                        sb -> new InlineString((optOut ? sb : sb.deleteCharAt(sb.length() - 1))),
                        Collector.Characteristics.CONCURRENT));
    }

    private static int outdent(List<InlineString.ref> lines) {
        // Note: outdent is guaranteed to be zero or positive number.
        // If there isn't a non-blank line then the last must be blank
        int outdent = Integer.MAX_VALUE;
        for (var line : lines) {
            int leadingWhitespace = line.indexOfNonWhitespace();
            if (leadingWhitespace != line.length()) {
                outdent = Integer.min(outdent, leadingWhitespace);
            }
        }
        var lastLine = lines.get(lines.size() - 1);
        if (lastLine.isBlank()) {
            outdent = Integer.min(outdent, lastLine.length());
        }
        return outdent;
    }

    /**
     * Returns a string whose value is this string, with escape sequences
     * translated as if in a string literal.
     * <p>
     * Escape sequences are translated as follows;
     * <table class="striped">
     *   <caption style="display:none">Translation</caption>
     *   <thead>
     *   <tr>
     *     <th scope="col">Escape</th>
     *     <th scope="col">Name</th>
     *     <th scope="col">Translation</th>
     *   </tr>
     *   </thead>
     *   <tbody>
     *   <tr>
     *     <th scope="row">{@code \u005Cb}</th>
     *     <td>backspace</td>
     *     <td>{@code U+0008}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005Ct}</th>
     *     <td>horizontal tab</td>
     *     <td>{@code U+0009}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005Cn}</th>
     *     <td>line feed</td>
     *     <td>{@code U+000A}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005Cf}</th>
     *     <td>form feed</td>
     *     <td>{@code U+000C}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005Cr}</th>
     *     <td>carriage return</td>
     *     <td>{@code U+000D}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005Cs}</th>
     *     <td>space</td>
     *     <td>{@code U+0020}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005C"}</th>
     *     <td>double quote</td>
     *     <td>{@code U+0022}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005C'}</th>
     *     <td>single quote</td>
     *     <td>{@code U+0027}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005C\u005C}</th>
     *     <td>backslash</td>
     *     <td>{@code U+005C}</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005C0 - \u005C377}</th>
     *     <td>octal escape</td>
     *     <td>code point equivalents</td>
     *   </tr>
     *   <tr>
     *     <th scope="row">{@code \u005C<line-terminator>}</th>
     *     <td>continuation</td>
     *     <td>discard</td>
     *   </tr>
     *   </tbody>
     * </table>
     *
     * @implNote
     * This method does <em>not</em> translate Unicode escapes such as "{@code \u005cu2022}".
     * Unicode escapes are translated by the Java compiler when reading input characters and
     * are not part of the string literal specification.
     *
     * @throws IllegalArgumentException when an escape sequence is malformed.
     *
     * @return String with escape sequences translated.
     *
     * @jls 3.10.7 Escape Sequences
     *
     * @since 15
     */
    public InlineString translateEscapes() {
        if (isEmpty()) {
            return EMPTY_STRING;
        }
        char[] chars = toCharArray();
        int length = chars.length;
        int from = 0;
        int to = 0;
        while (from < length) {
            char ch = chars[from++];
            if (ch == '\\') {
                ch = from < length ? chars[from++] : '\0';
                switch (ch) {
                    case 'b':
                        ch = '\b';
                        break;
                    case 'f':
                        ch = '\f';
                        break;
                    case 'n':
                        ch = '\n';
                        break;
                    case 'r':
                        ch = '\r';
                        break;
                    case 's':
                        ch = ' ';
                        break;
                    case 't':
                        ch = '\t';
                        break;
                    case '\'':
                    case '\"':
                    case '\\':
                        // as is
                        break;
                    case '0': case '1': case '2': case '3':
                    case '4': case '5': case '6': case '7':
                        int limit = Integer.min(from + (ch <= '3' ? 2 : 1), length);
                        int code = ch - '0';
                        while (from < limit) {
                            ch = chars[from];
                            if (ch < '0' || '7' < ch) {
                                break;
                            }
                            from++;
                            code = (code << 3) | (ch - '0');
                        }
                        ch = (char)code;
                        break;
                    case '\n':
                        continue;
                    case '\r':
                        if (from < length && chars[from] == '\n') {
                            from++;
                        }
                        continue;
                    default: {
                        String msg = String.format(
                                "Invalid escape sequence: \\%c \\\\u%04X",
                                ch, (int)ch);
                        throw new IllegalArgumentException(msg);
                    }
                }
            }

            chars[to++] = ch;
        }

        return new InlineString(chars, 0, to);
    }

    /**
     * This method allows the application of a function to {@code this}
     * string. The function should expect a single String argument
     * and produce an {@code R} result.
     * <p>
     * Any exception thrown by {@code f.apply()} will be propagated to the
     * caller.
     *
     * @param f    a function to apply
     *
     * @param <R>  the type of the result
     *
     * @return     the result of applying the function to this string
     *
     * @see java.util.function.Function
     *
     * @since 12
     */
    public <R> R transform(Function<? super InlineString.ref, ? extends R> f) {
        return f.apply(this);
    }

    /**
     * Return a {@code String} object similar to this object.
     *
     * @return  the equivalent {@code String} object.
     */
    public String toString() {
        assert isValid();
        return Utils.newStringValueCoder(content(), coder());
    }

    public InlineStringCodePointCursor codePointCursor() {
        return new InlineStringCodePointCursor(this);
    }

    /**
     * Returns a stream of {@code int} zero-extending the {@code char} values
     * from this sequence.  Any char which maps to a <a
     * href="{@docRoot}/java.base/java/lang/Character.html#unicode">surrogate code
     * point</a> is passed through uninterpreted.
     *
     * @return an IntStream of char values from this sequence
     * @since 9
     */
    @Override
    public IntStream chars() {
        Spliterator.OfInt charsSpliterator;
        if (isCompressed()) {
            charsSpliterator = StringCompressed.charSpliterator(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            charsSpliterator = StringLatin1.charsSpliterator(value, Spliterator.IMMUTABLE);
        } else {
            charsSpliterator = StringUTF16.charsSpliterator(value, Spliterator.IMMUTABLE);
        }
        return StreamSupport.intStream(charsSpliterator, false);
    }


    /**
     * Returns a stream of code point values from this sequence.  Any surrogate
     * pairs encountered in the sequence are combined as if by {@linkplain
     * Character#toCodePoint Character.toCodePoint} and the result is passed
     * to the stream. Any other code units, including ordinary BMP characters,
     * unpaired surrogates, and undefined code units, are zero-extended to
     * {@code int} values which are then passed to the stream.
     *
     * @return an IntStream of Unicode code points from this sequence
     * @since 9
     */
    @Override
    public IntStream codePoints() {
        Spliterator.OfInt charsSpliterator;
        if (isCompressed()) {
            charsSpliterator = StringCompressed.charSpliterator(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            charsSpliterator = StringLatin1.charsSpliterator(value, Spliterator.IMMUTABLE);
        } else {
            charsSpliterator = StringUTF16.codePointsSpliterator(value, Spliterator.IMMUTABLE);
        }
        return StreamSupport.intStream(charsSpliterator, false);
    }

    /**
     * Converts this string to a new character array.
     *
     * @return  a newly allocated character array whose length is the length
     *          of this string and whose contents are initialized to contain
     *          the character sequence represented by this string.
     */
    public char[] toCharArray() {
        if (isCompressed()) {
            return StringCompressed.toChars(firstHalf, secondHalf, length());
        } else if (isLatin1()) {
            return StringLatin1.toChars(value);
        } else {
            return StringUTF16.toChars(value);
        }
    }

    /**
     * Returns a formatted string using the specified format string and
     * arguments.
     *
     * <p> The locale always used is the one returned by {@link
     * java.util.Locale#getDefault(java.util.Locale.Category)
     * Locale.getDefault(Locale.Category)} with
     * {@link java.util.Locale.Category#FORMAT FORMAT} category specified.
     *
     * @param  format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the <a
     *         href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification.
     *
     * @return  A formatted string
     *
     * @see  java.util.Formatter
     * @since  1.5
     */
    public static InlineString format(InlineString format, Object... args) {
        return new InlineString(new Formatter().format(format.toString(), args).toString());
    }

    /**
     * Returns a formatted string using the specified locale, format string,
     * and arguments.
     *
     * @param  l
     *         The {@linkplain java.util.Locale locale} to apply during
     *         formatting.  If {@code l} is {@code null} then no localization
     *         is applied.
     *
     * @param  format
     *         A <a href="../util/Formatter.html#syntax">format string</a>
     *
     * @param  args
     *         Arguments referenced by the format specifiers in the format
     *         string.  If there are more arguments than format specifiers, the
     *         extra arguments are ignored.  The number of arguments is
     *         variable and may be zero.  The maximum number of arguments is
     *         limited by the maximum dimension of a Java array as defined by
     *         <cite>The Java Virtual Machine Specification</cite>.
     *         The behaviour on a
     *         {@code null} argument depends on the
     *         <a href="../util/Formatter.html#syntax">conversion</a>.
     *
     * @throws  java.util.IllegalFormatException
     *          If a format string contains an illegal syntax, a format
     *          specifier that is incompatible with the given arguments,
     *          insufficient arguments given the format string, or other
     *          illegal conditions.  For specification of all possible
     *          formatting errors, see the <a
     *          href="../util/Formatter.html#detail">Details</a> section of the
     *          formatter class specification
     *
     * @return  A formatted string
     *
     * @see  java.util.Formatter
     * @since  1.5
     */
    public static InlineString format(Locale l, InlineString format, Object... args) {
        return new InlineString(new Formatter(l).format(format.toString(), args).toString());
    }

    /**
     * Formats using this string as the format string, and the supplied
     * arguments.
     *
     * @implSpec This method is equivalent to {@code String.format(this, args)}.
     *
     * @param  args
     *         Arguments referenced by the format specifiers in this string.
     *
     * @return  A formatted string
     *
     * @see  java.lang.String#format(String,Object...)
     * @see  java.util.Formatter
     *
     * @since 15
     *
     */
    public InlineString formatted(Object... args) {
        return new InlineString(new Formatter().format(this.toString(), args).toString());
    }

    /**
     * Returns the string representation of the {@code Object} argument.
     *
     * @param   obj   an {@code Object}.
     * @return  if the argument is {@code null}, then a string equal to
     *          {@code "null"}; otherwise, the value of
     *          {@code obj.toString()} is returned.
     * @see     java.lang.Object#toString()
     */
    public static InlineString valueOf(Object obj) {
        return (obj == null) ? new InlineString("null") : new InlineString(obj.toString());
    }

    /**
     * Returns the string representation of the {@code char} array
     * argument. The contents of the character array are copied; subsequent
     * modification of the character array does not affect the returned
     * string.
     *
     * @param   data     the character array.
     * @return  a {@code String} that contains the characters of the
     *          character array.
     */
    public static InlineString valueOf(char[] data) {
        return new InlineString(data);
    }

    /**
     * Returns the string representation of a specific subarray of the
     * {@code char} array argument.
     * <p>
     * The {@code offset} argument is the index of the first
     * character of the subarray. The {@code count} argument
     * specifies the length of the subarray. The contents of the subarray
     * are copied; subsequent modification of the character array does not
     * affect the returned string.
     *
     * @param   data     the character array.
     * @param   offset   initial offset of the subarray.
     * @param   count    length of the subarray.
     * @return  a {@code String} that contains the characters of the
     *          specified subarray of the character array.
     * @throws    IndexOutOfBoundsException if {@code offset} is
     *          negative, or {@code count} is negative, or
     *          {@code offset+count} is larger than
     *          {@code data.length}.
     */
    public static InlineString valueOf(char[] data, int offset, int count) {
        return new InlineString(data, offset, count);
    }

    /**
     * Equivalent to {@link #valueOf(char[], int, int)}.
     *
     * @param   data     the character array.
     * @param   offset   initial offset of the subarray.
     * @param   count    length of the subarray.
     * @return  a {@code String} that contains the characters of the
     *          specified subarray of the character array.
     * @throws    IndexOutOfBoundsException if {@code offset} is
     *          negative, or {@code count} is negative, or
     *          {@code offset+count} is larger than
     *          {@code data.length}.
     */
    public static InlineString copyValueOf(char[] data, int offset, int count) {
        return new InlineString(data, offset, count);
    }

    /**
     * Equivalent to {@link #valueOf(char[])}.
     *
     * @param   data   the character array.
     * @return  a {@code String} that contains the characters of the
     *          character array.
     */
    public static InlineString copyValueOf(char[] data) {
        return new InlineString(data);
    }

    /**
     * Returns the string representation of the {@code boolean} argument.
     *
     * @param   b   a {@code boolean}.
     * @return  if the argument is {@code true}, a string equal to
     *          {@code "true"} is returned; otherwise, a string equal to
     *          {@code "false"} is returned.
     */
    public static InlineString valueOf(boolean b) {
        return b ? new InlineString("true") : new InlineString("false");
    }

    /**
     * Returns the string representation of the {@code char}
     * argument.
     *
     * @param   c   a {@code char}.
     * @return  a string of length {@code 1} containing
     *          as its single character the argument {@code c}.
     */
    public static InlineString valueOf(char c) {
        if (Utils.COMPRESSED_STRINGS && StringLatin1.canEncode(c)) {
            return new InlineString(SMALL_STRING_VALUE, 1, c, 0);
        } else if (Utils.COMPACT_STRINGS && StringLatin1.canEncode(c)) {
            return new InlineString(StringLatin1.toBytes(c), 1, Utils.LATIN1, 0);
        } else {
            return new InlineString(StringUTF16.toBytes(c), 1, Utils.UTF16, 0);
        }
    }

    /**
     * Returns the string representation of the {@code int} argument.
     * <p>
     * The representation is exactly the one returned by the
     * {@code Integer.toString} method of one argument.
     *
     * @param   i   an {@code int}.
     * @return  a string representation of the {@code int} argument.
     * @see     java.lang.Integer#toString(int, int)
     */
    public static InlineString valueOf(int i) {
        return new InlineString(Integer.toString(i));
    }

    /**
     * Returns the string representation of the {@code long} argument.
     * <p>
     * The representation is exactly the one returned by the
     * {@code Long.toString} method of one argument.
     *
     * @param   l   a {@code long}.
     * @return  a string representation of the {@code long} argument.
     * @see     java.lang.Long#toString(long)
     */
    public static InlineString valueOf(long l) {
        return new InlineString(Long.toString(l));
    }

    /**
     * Returns the string representation of the {@code float} argument.
     * <p>
     * The representation is exactly the one returned by the
     * {@code Float.toString} method of one argument.
     *
     * @param   f   a {@code float}.
     * @return  a string representation of the {@code float} argument.
     * @see     java.lang.Float#toString(float)
     */
    public static InlineString valueOf(float f) {
        return new InlineString(Float.toString(f));
    }

    /**
     * Returns the string representation of the {@code double} argument.
     * <p>
     * The representation is exactly the one returned by the
     * {@code Double.toString} method of one argument.
     *
     * @param   d   a {@code double}.
     * @return  a  string representation of the {@code double} argument.
     * @see     java.lang.Double#toString(double)
     */
    public static InlineString valueOf(double d) {
        return new InlineString(Double.toString(d));
    }

    /**
     * Returns a string whose value is the concatenation of this
     * string repeated {@code count} times.
     * <p>
     * If this string is empty or count is zero then the empty
     * string is returned.
     *
     * @param   count number of times to repeat
     *
     * @return  A string composed of this string repeated
     *          {@code count} times or the empty string if this
     *          string is empty or count is zero
     *
     * @throws  IllegalArgumentException if the {@code count} is
     *          negative.
     *
     * @since 11
     */
    public InlineString repeat(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (count == 1) {
            return this;
        }
        if (isEmpty() || count == 0) {
            return EMPTY_STRING;
        }
        long coder = coder();
        int dataLen = length() << coder;
        if (Integer.MAX_VALUE / count < dataLen) {
            throw new OutOfMemoryError("Required index exceeds implementation limit");
        }
        int limit = dataLen * count;
        byte[] multiple;
        if (isCompressed()) {
            if (StringCompressed.compressible(limit)) {
                // since count > 1 and limit <= 16, length <= 8
                long firstHalf = 0;
                int limitInBits = limit * Byte.SIZE;
                int step = length() * Byte.SIZE;
                int shiftStep = length() * Byte.SIZE;
                int i = 0;
                for (long temp = this.firstHalf; i < Math.min(limitInBits, Long.SIZE);
                        i += step, temp >>>= shiftStep) {
                    firstHalf |= temp;
                }
                // if i == 8 the first value need to be zero
                long secondHalf = (i == Long.SIZE)
                        ? 0
                        : this.firstHalf << -i;
                for (long temp = this.firstHalf >>> i; i < limitInBits;
                        i += step, temp >>>= shiftStep) {
                    secondHalf |= temp;
                }
                return new InlineString(SMALL_STRING_VALUE, limit, firstHalf, secondHalf);
            } else {
                multiple = StringConcatHelper.newArray(limit, Utils.LATIN1);
                // limit > 16
                StringCompressed.decompress(firstHalf, secondHalf, multiple, 0, Long.BYTES * 2);
            }
        } else {
            multiple = StringConcatHelper.newArray(limit, Utils.LATIN1);
            System.arraycopy(value, 0, multiple, 0, dataLen);
        }
        int copied = dataLen;
        for (; copied < limit - copied; copied <<= 1) {
            System.arraycopy(multiple, 0, multiple, copied, copied);
        }
        System.arraycopy(multiple, 0, multiple, copied, limit - copied);
        return new InlineString(multiple, limit >>> coder, coder, 0);
    }

    ////////////////////////////////////////////////////////////////

    private InlineString(StringData data) {
        this.firstHalf = data.firstHalf();
        this.secondHalf = data.secondHalf();
        this.scope = data.scope();
    }

    boolean isCompressed() {
        return StringData.COMPRESSED_STRINGS && secondHalf >= 0;
    }

    long firstHalf() {
        assert isCompressed();
        return firstHalf;
    }

    long secondHalf() {
        assert isCompressed();
        return secondHalf;
    }

    int compressedLength() {
        assert isCompressed();
        return (int)(secondHalf >>> StringCompressed.LENGTH_SHIFT);
    }

    long address() {
        assert !isCompressed();
        return firstHalf;
    }

    int nonCompressedLength() {
        assert !isCompressed();
        return (int)-secondHalf;
    }

    Scope scope() {
        assert !isCompressed();
        return scope;
    }

    private boolean isValid() {
        if (isCompressed()) {
            boolean first = length() <= StringCompressed.COMPRESS_THRESHOLD && length() >= 0;
            boolean second = LongVector.zero(LongVector.SPECIES_128)
                    .withLane(0, firstHalf)
                    .withLane(1, secondHalf)
                    .reinterpretAsBytes()
                    .compare(VectorOperators.EQ, (byte)0)
                    .or(Helper.BYTE_INDEX_VECTOR.compare(VectorOperators.LT, (byte)length()))
                    .allTrue();
            return first && second;
        } else if (isLatin1()) {
            return value.length == length() && length() > StringCompressed.COMPRESS_THRESHOLD && this.secondHalf == 0;
        } else {
            return value.length == (length() << 1) && this.firstHalf == Utils.UTF16 && this.secondHalf == 0;
        }
    }

    /**
     * Returns an {@link Optional} containing the nominal descriptor for this
     * instance.
     *
     * @return an {@link Optional} describing the {@linkplain InlineString} instance
     */
    @Override
    public Optional<DynamicConstantDesc<InlineString>> describeConstable() {
        var str = this.toString();
        return Optional.of(DynamicConstantDesc.<InlineString>ofNamed(Utils.INLINE_STRING_CONSTRUCTOR_STRING,
                str,
                Utils.INLINE_STRING_CLASS,
                str));
    }
}


