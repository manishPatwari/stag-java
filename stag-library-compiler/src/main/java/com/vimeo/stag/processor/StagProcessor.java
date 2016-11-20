/*
 * The MIT License (MIT)
 * <p/>
 * Copyright (c) 2016 Vimeo
 * <p/>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p/>
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 * <p/>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.vimeo.stag.processor;

import com.google.auto.service.AutoService;
import com.google.gson.annotations.SerializedName;
import com.squareup.javapoet.JavaFile;
import com.vimeo.stag.GsonAdapterKey;
import com.vimeo.stag.processor.generators.StagGenerator;
import com.vimeo.stag.processor.generators.TypeAdapterFactoryGenerator;
import com.vimeo.stag.processor.generators.TypeAdapterGenerator;
import com.vimeo.stag.processor.generators.TypeTokenConstantsGenerator;
import com.vimeo.stag.processor.generators.model.AnnotatedClass;
import com.vimeo.stag.processor.generators.model.ClassInfo;
import com.vimeo.stag.processor.generators.model.SupportedTypesModel;
import com.vimeo.stag.processor.utils.DebugLog;
import com.vimeo.stag.processor.utils.ElementUtils;
import com.vimeo.stag.processor.utils.FileGenUtils;
import com.vimeo.stag.processor.utils.KnownTypeAdapterFactoriesUtils;
import com.vimeo.stag.processor.utils.TypeUtils;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.Filer;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

@AutoService(Processor.class)
@SupportedAnnotationTypes("com.google.gson.annotations.SerializedName")
@SupportedSourceVersion(SourceVersion.RELEASE_7)
public final class StagProcessor extends AbstractProcessor {

    public static final boolean DEBUG = false;
    private boolean mHasBeenProcessed;
    private final Set<String> mSupportedTypes = new HashSet<>();

    @Override
    public Set<String> getSupportedAnnotationTypes() {
        Set<String> set = new HashSet<>();
        set.add(SerializedName.class.getCanonicalName());
        return set;
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.RELEASE_7;
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (mHasBeenProcessed) {
            return true;
        }
        TypeUtils.initialize(processingEnv.getTypeUtils());
        ElementUtils.initialize(processingEnv.getElementUtils());

        DebugLog.log("\nBeginning @SerializedName annotation processing\n");

        mHasBeenProcessed = true;
        Map<Element, List<VariableElement>> variableMap = new HashMap<>();

        /**
         * This is to check if the annotation is present at the class level.
         */
        Set<? extends Element> rootElements = roundEnv.getRootElements();
        for (Element rootElement : rootElements) {
            if (rootElement.getAnnotation(GsonAdapterKey.class) != null) {
                List<? extends Element> enclosedElements = rootElement.getEnclosedElements();
                for (Element enclosedElement : enclosedElements) {
                    if (enclosedElement instanceof VariableElement) {
                        final VariableElement variableElement = (VariableElement) enclosedElement;
                        Element enclosingElement = variableElement.getEnclosingElement();
                        if (enclosingElement.getKind() != ElementKind.ENUM) {
                            Set<Modifier> modifiers = variableElement.getModifiers();
                            TypeMirror enclosingClass = enclosingElement.asType();
                            if (!TypeUtils.isParameterizedType(enclosingClass) || TypeUtils.isConcreteType(enclosingClass)) {
                                if (!modifiers.contains(Modifier.FINAL) || !modifiers.contains(Modifier.STATIC)) {
                                    checkModifiers(variableElement, modifiers);
                                    mSupportedTypes.add(enclosingClass.toString());
                                    addToListMap(variableMap, enclosingElement, variableElement);
                                }
                            }
                        }
                    } else if (enclosedElement instanceof TypeElement) {
                        if (enclosedElement.getKind() != ElementKind.ENUM) {
                            mSupportedTypes.add(enclosedElement.asType().toString());
                            addToListMap(variableMap, enclosedElement, null);
                        }
                    }
                }
            }
        }

        Filer filer = processingEnv.getFiler();
        try {
            for (Entry<Element, List<VariableElement>> entry : variableMap.entrySet()) {
                SupportedTypesModel.getInstance()
                        .addSupportedType(new AnnotatedClass(entry.getKey(), entry.getValue()));
            }
            mSupportedTypes.addAll(KnownTypeAdapterFactoriesUtils.loadKnownTypes(processingEnv));

            StagGenerator adapterGenerator = new StagGenerator(filer, mSupportedTypes);
            adapterGenerator.generateTypeAdapterFactory();

            TypeTokenConstantsGenerator typeTokenConstantsGenerator = new TypeTokenConstantsGenerator(filer);

            Set<Element> list = SupportedTypesModel.getInstance().getSupportedElements();
            for (Element element : list) {
                if (TypeUtils.isConcreteType(element)) {
                    ClassInfo classInfo = new ClassInfo(element.asType());
                    TypeAdapterGenerator independentAdapter = new TypeAdapterGenerator(classInfo);
                    JavaFile javaFile = JavaFile.builder(classInfo.getPackageName(),
                                                         independentAdapter.getTypeAdapterSpec(
                                                                 typeTokenConstantsGenerator)).build();
                    FileGenUtils.writeToFile(javaFile, filer);

                    TypeAdapterFactoryGenerator factoryGenerator = new TypeAdapterFactoryGenerator(classInfo);
                    javaFile = JavaFile.builder(classInfo.getPackageName(),
                                                factoryGenerator.getTypeAdapterFactorySpec()).build();
                    FileGenUtils.writeToFile(javaFile, filer);
                }
            }

            typeTokenConstantsGenerator.generateTypeTokenConstants();
            KnownTypeAdapterFactoriesUtils.writeKnownTypes(processingEnv, mSupportedTypes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        DebugLog.log("\nSuccessfully processed @SerializedName annotations\n");

        return true;
    }

    private void checkModifiers(VariableElement variableElement, Set<Modifier> modifiers) {
        if (modifiers.contains(Modifier.FINAL)) {
            if (!modifiers.contains(Modifier.STATIC)) {
                throw new RuntimeException("Unable to access field \"" +
                        variableElement.getSimpleName().toString() + "\" in class " +
                        variableElement.getEnclosingElement().asType() +
                        ", field must not be final.");
            }
        } else if (modifiers.contains(Modifier.PRIVATE)) {
            throw new RuntimeException("Unable to access field \"" +
                    variableElement.getSimpleName().toString() + "\" in class " +
                    variableElement.getEnclosingElement().asType() +
                    ", field must not be private.");
        }
    }

    private static void addToListMap(@NotNull Map<Element, List<VariableElement>> map, @Nullable Element key,
                                     @Nullable VariableElement value) {
        if (key == null) {
            return;
        }
        List<VariableElement> list = map.get(key);
        if (list == null) {
            list = new ArrayList<>();
        }
        if (value != null) {
            list.add(value);
        }
        map.put(key, list);
    }
}