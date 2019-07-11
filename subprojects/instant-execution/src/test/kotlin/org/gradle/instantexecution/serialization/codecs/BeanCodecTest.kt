/*
 * Copyright 2019 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.instantexecution.serialization.codecs

import com.nhaarman.mockitokotlin2.mock

import org.gradle.instantexecution.extensions.uncheckedCast
import org.gradle.instantexecution.runToCompletion
import org.gradle.instantexecution.serialization.DefaultReadContext
import org.gradle.instantexecution.serialization.DefaultWriteContext
import org.gradle.instantexecution.serialization.IsolateOwner
import org.gradle.instantexecution.serialization.beans.BeanPropertyReader
import org.gradle.instantexecution.serialization.withIsolate

import org.gradle.internal.serialize.kryo.KryoBackedDecoder
import org.gradle.internal.serialize.kryo.KryoBackedEncoder

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.OutputStream


class BeanCodecTest {

    @Test
    fun `can handle deeply nested graphs`() {

        val deepGraph = Peano.fromInt(1024)

        val read = roundtrip(deepGraph)

        assertThat(
            deepGraph.toInt(),
            equalTo(read.toInt())
        )
    }

    private
    fun <T : Any> roundtrip(graph: T): T =
        writeToByteArray(graph).let(::readFrom)!!.uncheckedCast()

    private
    fun writeToByteArray(graph: Any): ByteArray {
        val outputStream = ByteArrayOutputStream()
        writeTo(outputStream, graph)
        return outputStream.toByteArray()
    }

    private
    fun writeTo(outputStream: OutputStream, graph: Any) {
        KryoBackedEncoder(outputStream).use { encoder ->
            writeContextFor(encoder).run {
                withIsolate(IsolateOwner.OwnerGradle(mock())) {
                    runToCompletion {
                        write(graph)
                    }
                }
            }
        }
    }

    private
    fun readFrom(bytes: ByteArray) =
        readFrom(ByteArrayInputStream(bytes))

    private
    fun readFrom(inputStream: ByteArrayInputStream) =
        readContextFor(inputStream).run {
            initClassLoader(javaClass.classLoader)
            withIsolate(IsolateOwner.OwnerGradle(mock())) {
                runToCompletion {
                    read()
                }
            }
        }

    private
    fun writeContextFor(encoder: KryoBackedEncoder) =
        DefaultWriteContext(
            encodings = codecs(),
            encoder = encoder,
            logger = mock(),
            problemHandler = mock()
        )

    private
    fun readContextFor(inputStream: ByteArrayInputStream) =
        DefaultReadContext(
            decoding = codecs(),
            decoder = KryoBackedDecoder(inputStream),
            logger = mock(),
            beanPropertyReaderFactory = BeanPropertyReader.factoryFor(mock())
        )

    private
    fun codecs() = Codecs(
        directoryFileTreeFactory = mock(),
        fileCollectionFactory = mock(),
        fileResolver = mock(),
        instantiator = mock(),
        listenerManager = mock()
    )

    @Test
    fun `Peano sanity check`() {

        assertThat(
            Peano.fromInt(0),
            equalTo<Peano>(Peano.Zero)
        )

        assertThat(
            Peano.fromInt(1024).toInt(),
            equalTo(1024)
        )
    }

    sealed class Peano {

        companion object {

            fun fromInt(n: Int): Peano {
                require(n >= 0)
                tailrec fun build(n: Int, acc: Peano): Peano = when (n) {
                    0 -> acc
                    else -> build(n - 1, Succ(acc))
                }
                return build(n, Zero)
            }
        }

        fun toInt(): Int {
            tailrec fun count(n: Peano, acc: Int): Int = when (n) {
                is Zero -> acc
                is Succ -> count(n.n, acc + 1)
            }
            return count(this, 0)
        }

        object Zero : Peano()

        data class Succ(val n: Peano) : Peano()
    }
}
