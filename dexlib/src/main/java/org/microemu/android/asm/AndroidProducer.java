/**
 * MicroEmulator
 * Copyright (C) 2008 Bartek Teodorczyk <barteo@barteo.net>
 * Copyright (C) 2017-2018 Nikita Shakarun
 * Copyright (C) 2021-2022 Yury Kharchenko
 * <p>
 * It is licensed under the following two licenses as alternatives:
 * 1. GNU Lesser General Public License (the "LGPL") version 2.1 or any newer version
 * 2. Apache License (the "AL") Version 2.0
 * <p>
 * You may not use this file except in compliance with at least one of
 * the above two licenses.
 * <p>
 * You may obtain a copy of the LGPL at
 * http://www.gnu.org/licenses/old-licenses/lgpl-2.1.txt
 * <p>
 * You may obtain a copy of the AL at
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the LGPL or the AL for the specific language governing permissions and
 * limitations.
 *
 * @version $Id$
 */

package org.microemu.android.asm;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;

public class AndroidProducer {

	public static byte[] instrument(final byte[] classData, String classFileName)
			throws IllegalArgumentException {
		return instrument(classData, classFileName, null);
	}

	public static byte[] instrument(final byte[] classData, String classFileName,
									MethodRenamer renamer) throws IllegalArgumentException {
		ClassReader cr = new ClassReader(classData);
		if (!cr.getClassName().equals(classFileName.substring(0, classFileName.length() - 6))) {
			throw new IllegalArgumentException("Class name does not match path");
		}

		// Instrumentation changes bytecode, so the original StackMapTable is invalid:
		// recompute frames. COMPUTE_MAXS alone makes dx fail on class version >= 50
		// ("SimException: stack: overflow"), common in ports built with modern JDKs.
		ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES) {
			@Override
			protected String getCommonSuperClass(String type1, String type2) {
				if (type1.equals(type2)) {
					return type1;
				}
				if ("java/lang/Object".equals(type1) || "java/lang/Object".equals(type2)) {
					return "java/lang/Object";
				}
				try {
					return super.getCommonSuperClass(type1, type2);
				} catch (Throwable t) {
					// MIDP classes are not on the classpath here; fall back to Object
					return "java/lang/Object";
				}
			}
		};
		ClassVisitor cv = new AndroidClassVisitor(cw, renamer);
		cr.accept(cv, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);

		return cw.toByteArray();
	}
}
