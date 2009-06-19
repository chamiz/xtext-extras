/*******************************************************************************
 * Copyright (c) 2008, 2009 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 *******************************************************************************/
package org.eclipse.xtext.generator.ecore;

import static org.eclipse.xtext.EcoreUtil2.collect;
import static org.eclipse.xtext.EcoreUtil2.typeSelect;
import static org.eclipse.xtext.XtextPackage.GENERATED_METAMODEL__EPACKAGE;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.log4j.Logger;
import org.eclipse.emf.codegen.ecore.generator.Generator;
import org.eclipse.emf.codegen.ecore.generator.GeneratorAdapterFactory;
import org.eclipse.emf.codegen.ecore.genmodel.GenJDKLevel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModel;
import org.eclipse.emf.codegen.ecore.genmodel.GenModelPackage;
import org.eclipse.emf.codegen.ecore.genmodel.GenPackage;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenBaseGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenClassGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenEnumGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenModelGeneratorAdapter;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenModelGeneratorAdapterFactory;
import org.eclipse.emf.codegen.ecore.genmodel.generator.GenPackageGeneratorAdapter;
import org.eclipse.emf.common.notify.Adapter;
import org.eclipse.emf.common.util.BasicMonitor;
import org.eclipse.emf.common.util.Diagnostic;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EPackage;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.resource.ResourceSet;
import org.eclipse.emf.ecore.resource.Resource.Factory;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.emf.ecore.util.EcoreUtil.Copier;
import org.eclipse.emf.ecore.xmi.impl.EcoreResourceFactoryImpl;
import org.eclipse.emf.mwe.core.ConfigurationException;
import org.eclipse.xpand2.XpandExecutionContext;
import org.eclipse.xtext.AbstractMetamodelDeclaration;
import org.eclipse.xtext.GeneratedMetamodel;
import org.eclipse.xtext.Grammar;
import org.eclipse.xtext.GrammarUtil;
import org.eclipse.xtext.generator.AbstractGeneratorFragment;
import org.eclipse.xtext.generator.IGeneratorFragment;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.eclipse.xtext.util.LineFilterOutputStream;
import org.eclipse.xtext.util.Strings;

/**
 * A {@link IGeneratorFragment} that saves the generated ecore models and creates appropriate EMF generators. Then it
 * runs the EMF generator to create the EMF classes for the generated Ecore models.
 * 
 * @author Michael Clay
 */
public class EcoreGeneratorFragment extends AbstractGeneratorFragment {

	private static final String MODEL_PLUGIN_ID = "modelPluginID";
	private static Logger log = Logger.getLogger(EcoreGeneratorFragment.class);

	{
		if (!Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().containsKey("genmodel"))
			Resource.Factory.Registry.INSTANCE.getExtensionToFactoryMap().put("genmodel",
					new org.eclipse.emf.ecore.xmi.impl.XMIResourceFactoryImpl());
		GenModelPackage.eINSTANCE.getGenAnnotation();
	}

	@Override
	public String[] getRequiredBundlesRt(Grammar grammar) {
		return new String[] { "org.eclipse.emf.ecore", "org.eclipse.emf.common" };
	}

	@Override
	protected List<Object> getParameters(Grammar grammar) {
		return Collections.singletonList((Object) getBasePackage(grammar));
	}

	@Override
	public String[] getExportedPackagesRt(Grammar grammar) {
		List<GeneratedMetamodel> typeSelect = org.eclipse.xtext.EcoreUtil2.typeSelect(grammar
				.getMetamodelDeclarations(), GeneratedMetamodel.class);
		Set<String> exportedPackages = new LinkedHashSet<String>();
		for (GeneratedMetamodel generatedMetamodel : typeSelect) {
			final String modelPackage = Strings.skipLastToken(getGeneratedEPackageName(grammar, generatedMetamodel
					.getEPackage()), ".");
			exportedPackages.add(modelPackage);
			exportedPackages.add(modelPackage + ".impl");
			exportedPackages.add(modelPackage + ".util");
		}
		return exportedPackages.toArray(new String[exportedPackages.size()]);
	}

	@Override
	public void generate(Grammar grammar, XpandExecutionContext ctx) {
		super.generate(grammar, ctx);

		List<GeneratedMetamodel> list = typeSelect(grammar.getMetamodelDeclarations(), GeneratedMetamodel.class);
		List<EPackage> packs = collect(list, GENERATED_METAMODEL__EPACKAGE, EPackage.class);
		if (!packs.isEmpty()) {
			ResourceSet rs = new XtextResourceSet();
			Copier copier = new EcoreUtil.Copier();
			copyGeneratedMetamodelsTo(grammar.getUsedGrammars(), rs, copier);
			List<EPackage> copies = new ArrayList<EPackage>(packs.size());
			for (EPackage pack : packs) {
				copies.add((EPackage) copier.copy(pack));
			}
			copier.copyReferences();

			String javaPath, xmiPath;
			if (javaModelDirectory == null || "".equals(javaModelDirectory))
				javaPath = ctx.getOutput().getOutlet(org.eclipse.xtext.generator.Generator.SRC_GEN).getPath();
			else
				javaPath = javaModelDirectory;
			if (xmiModelDirectory == null || "".equals(xmiModelDirectory))
				xmiPath = javaPath + "/"
						+ grammar.getName().substring(0, grammar.getName().lastIndexOf('.')).replace('.', '/');
			else
				xmiPath = xmiModelDirectory;
			if ((this.modelPluginID == null || "".equals(this.modelPluginID))
					&& (ctx.getVariable(MODEL_PLUGIN_ID) != null)) {
				this.modelPluginID = (String) ctx.getVariable(MODEL_PLUGIN_ID).getValue();
			}
			if (this.editDirectory == null || "".equals(this.editDirectory)) {
				this.editDirectory = ctx.getOutput().getOutlet(org.eclipse.xtext.generator.Generator.PLUGIN_RT)
						.getPath()
						+ ".edit/src";
			}
			if ((this.editPluginID == null || "".equals(this.editPluginID)) && (this.modelPluginID != null)) {
				this.editPluginID = this.modelPluginID + ".edit";
			}
			if (this.editorDirectory == null || "".equals(this.editorDirectory)) {
				this.editorDirectory = ctx.getOutput().getOutlet(org.eclipse.xtext.generator.Generator.PLUGIN_RT)
						.getPath()
						+ ".editor/src";
			}
			if ((this.editorPluginID == null || "".equals(this.editorPluginID)) && (this.modelPluginID != null)) {
				this.editorPluginID = this.modelPluginID + ".editor";
			}
			generateEcoreJavaClasses(rs, copies, getBasePackage(grammar), javaPath, xmiPath, grammar);
		}
	}

	private void copyGeneratedMetamodelsTo(List<Grammar> list, ResourceSet rs, Copier copier) {
		List<EPackage> generatedEPackages = new ArrayList<EPackage>(3);
		collectGeneratedMetamodels(generatedEPackages, list, new HashSet<Grammar>());
		Factory factory = new EcoreResourceFactoryImpl();
		for (EPackage copyMe : generatedEPackages) {
			Resource resource = factory.createResource(URI.createURI(copyMe.getNsURI()));
			resource.getContents().add(copier.copy(copyMe));
			rs.getResources().add(resource);
		}
	}

	private void collectGeneratedMetamodels(List<EPackage> generatedEPackages, List<Grammar> grammars,
			Set<Grammar> visited) {
		for (Grammar g : grammars) {
			if (visited.add(g)) {
				for (AbstractMetamodelDeclaration decl : g.getMetamodelDeclarations()) {
					if (decl instanceof GeneratedMetamodel)
						generatedEPackages.add(decl.getEPackage());
				}
				collectGeneratedMetamodels(generatedEPackages, g.getUsedGrammars(), visited);
			}
		}
	}

	private String urisString;

	/**
	 * Sets the URIs for the generated EMF generator models (aka genmodels).
	 * 
	 * @param uris
	 */
	public void setGenModels(String uris) {
		if ("".equals(uris))
			return;
		this.urisString = uris;
	}

	private Collection<? extends GenPackage> getUsedGenPackages(ResourceSet rs) {
		Set<GenPackage> result = new LinkedHashSet<GenPackage>();
		if (urisString != null) {
			for (String uri : urisString.split(",")) {
				try {
					Resource resource = rs.getResource(URI.createURI(uri.trim()), true);
					GenModel genmodel = (GenModel) resource.getContents().get(0);
					EList<GenPackage> genPackages = genmodel.getGenPackages();
					for (GenPackage genPackage : genPackages) {
						genPackage.getEcorePackage().getEClassifiers();
						result.add(genPackage);
					}
				}
				catch (Exception e) {
					log.error("Couldn't find genmodel for uri '" + uri + "'");
					throw new RuntimeException(e);
				}
			}
		}
		return result;
	}

	public void generateEcoreJavaClasses(ResourceSet rs, Collection<? extends EPackage> packs,
			final String basePackage, final String javaPath, final String xmiPath, final Grammar grammar)
			throws ConfigurationException {
		Resource res2;
		Resource res;
		String modelName = grammar.getName().substring(grammar.getName().lastIndexOf('.') + 1);
		try {
			String prefix = new File(xmiPath).getCanonicalPath() + "/" + modelName;
			res2 = rs.createResource(URI.createFileURI(prefix + ".ecore"));
			res = rs.createResource(URI.createFileURI(prefix + ".genmodel"));
		}
		catch (IOException e1) {
			throw new IllegalStateException("Couldn't compute canonical path for "
					+ new File(xmiPath).getAbsolutePath());
		}

		GenModel genModel = GenModelPackage.eINSTANCE.getGenModelFactory().createGenModel();
		genModel.initialize(packs);
		genModel.setModelDirectory(toGenModelProjectPath(javaPath));
		genModel.setModelName(modelName);
		genModel.setModelPluginID(this.modelPluginID);
		genModel.setEditDirectory(toGenModelProjectPath(this.editDirectory));
		genModel.setEditorDirectory(toGenModelProjectPath(this.editorDirectory));
		genModel.setEditPluginID(this.editPluginID);
		genModel.setEditorPluginID(this.editorPluginID);

		genModel.setValidateModel(false);
		genModel.setForceOverwrite(true);
		genModel.setCanGenerate(true);
		genModel.setFacadeHelperClass(null);
		genModel.setBundleManifest(true);
		genModel.setUpdateClasspath(false);
		genModel.setComplianceLevel(GenJDKLevel.JDK50_LITERAL);
		genModel.setRootExtendsClass("org.eclipse.emf.ecore.impl.MinimalEObjectImpl$Container");

		for (GenPackage genPackage : genModel.getGenPackages()) {
			genPackage.setBasePackage(basePackage);
		}
		Collection<? extends GenPackage> usedGenPackages = getUsedGenPackages(rs);
		genModel.getUsedGenPackages().addAll(usedGenPackages);
		// write genmodel
		res.getContents().add(genModel);
		res2.getContents().addAll(packs);
		try {
			res2.save(null);
			res.save(null);
		}
		catch (IOException e) {
			log.error(e.getMessage(), e);
		}

		genModel.reconcile();

		Generator generator = new Generator();
		generator.getAdapterFactoryDescriptorRegistry().addDescriptor(GenModelPackage.eNS_URI,
				new GeneratorAdapterFactory.Descriptor() {
					public GeneratorAdapterFactory createAdapterFactory() {
						return new GenModelGeneratorAdapterFactory() {

							@Override
							public Adapter createGenEnumAdapter() {
								return new GenEnumGeneratorAdapter(this) {
									@Override
									protected OutputStream createOutputStream(URI workspacePath) throws Exception {
										return EcoreGeneratorFragment.this.createOutputStream(super
												.createOutputStream(workspacePath));
									}
								};
							}

							@Override
							public Adapter createGenClassAdapter() {
								return new GenClassGeneratorAdapter(this) {
									@Override
									protected OutputStream createOutputStream(URI workspacePath) throws Exception {
										return EcoreGeneratorFragment.this.createOutputStream(super
												.createOutputStream(workspacePath));
									}
								};
							}

							@Override
							public Adapter createGenPackageAdapter() {
								return new GenPackageGeneratorAdapter(this) {
									@Override
									protected OutputStream createOutputStream(URI workspacePath) throws Exception {
										return EcoreGeneratorFragment.this.createOutputStream(super
												.createOutputStream(workspacePath));
									}

								};
							}

							@Override
							public Adapter createGenModelAdapter() {
								if (genModelGeneratorAdapter == null) {
									genModelGeneratorAdapter = new GenModelGeneratorAdapter(this) {
										// we handle these ones on our own
										@Override
										protected void generateModelBuildProperties(GenModel genModel,
												org.eclipse.emf.common.util.Monitor monitor) {
										}

										@Override
										protected void generateModelManifest(GenModel genModel,
												org.eclipse.emf.common.util.Monitor monitor) {
										}

										@Override
										protected void generateModelPluginProperties(GenModel genModel,
												org.eclipse.emf.common.util.Monitor monitor) {
										}

										@Override
										protected void generateModelPluginClass(GenModel genModel,
												org.eclipse.emf.common.util.Monitor monitor) {
										}
									};
								}
								return genModelGeneratorAdapter;
							}
						};
					}
				});
		generator.setInput(genModel);
		Diagnostic diagnostic = generator.generate(genModel, GenBaseGeneratorAdapter.MODEL_PROJECT_TYPE,
				new BasicMonitor());

		if (diagnostic.getSeverity() != Diagnostic.OK)
			log.info(diagnostic);
	}

	public OutputStream createOutputStream(OutputStream stream) throws Exception {
		return new LineFilterOutputStream(stream, " * $Id" + "$");
	}

	/**
	 * required to match the path format as expected from {@link GenModelImpl#getProjectPath}
	 */
	private String toGenModelProjectPath(String path) {
		return null == path || "".equals(path) || path.startsWith("/") ? path : path.substring(path.indexOf("/"));
	}

	private String basePackage = null;
	private String javaModelDirectory = null;
	private String xmiModelDirectory = null;
	private String modelPluginID = null;
	private String editDirectory = null;
	private String editorDirectory = null;
	private String editPluginID = null;
	private String editorPluginID = null;

	/**
	 * Set the target Java package for the generated EMF-model code.
	 * 
	 * @param basePackage
	 */
	public void setBasePackage(String basePackage) {
		if ("".equals(basePackage.trim()))
			return;
		this.basePackage = basePackage;
	}

	public String getBasePackage(Grammar g) {
		if (basePackage == null)
			return GrammarUtil.getNamespace(g);
		return basePackage;
	}

	/**
	 * Sets the target directory for the generated EMF-model code. Only needed if you want to generate the EMF code into
	 * a separate plug-in.
	 * 
	 * @param dir
	 */
	public void setJavaModelDirectory(String dir) {
		javaModelDirectory = dir;
	}
	
	/**
	 * Sets the directory where the Ecore and Ecore generator model files reside.
	 *  
	 * @param dir
	 */
	public void setXmiModelDirectory(String dir) {
		xmiModelDirectory = dir;
	}

	/**
	 * Sets the ID of the generated EMF-model plug-in. Only needed if you want to generate the EMF code into a separate
	 * plug-in.
	 * 
	 * @param modelPluginId
	 */
	public void setModelPluginID(String modelPluginId) {
		modelPluginID = modelPluginId;
	}

	/**
	 * Sets the target directory for the generated EMF-edit code. Only needed if you want to generate an EMF edit
	 * plug-in.
	 * 
	 * @param editDirectory
	 */
	public void setEditDirectory(String editDirectory) {
		this.editDirectory = editDirectory;
	}

	/**
	 * Sets the target directory for the generated EMF-editor code. Only needed if you want to generate an EMF editor
	 * plug_in.
	 * 
	 * @param editDirectory
	 */
	public void setEditorDirectory(String editorDirectory) {
		this.editorDirectory = editorDirectory;
	}

	/**
	 * Sets the plug-in ID of the generated EMF edit plug-in. Only needed if you want to generate an EMF editor plug_in.
	 * 
	 * @param editPluginId
	 */
	public void setEditPluginID(String editPluginId) {
		editPluginID = editPluginId;
	}

	/**
	 * Sets the plug-in ID of the generated EMF editor plug-in. Only needed if you want to generate an EMF editor
	 * plug_in.
	 * 
	 * @param editPluginId
	 */
	public void setEditorPluginID(String editorPluginId) {
		editorPluginID = editorPluginId;
	}

	public String getGeneratedEPackageName(Grammar g, EPackage pack) {
		return getBasePackage(g) + "." + pack.getName() + "." + Strings.toFirstUpper(pack.getName()) + "Package";
	}
}
