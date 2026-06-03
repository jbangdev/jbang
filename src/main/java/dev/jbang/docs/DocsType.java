package dev.jbang.docs;

import java.util.ArrayList;
import java.util.List;

public class DocsType {

	public enum Kind {
		CLASS, INTERFACE, ENUM, RECORD
	}

	public static class DocsAnnotation {
		private String qualifiedName;

		public DocsAnnotation() {
		}

		public DocsAnnotation(String qualifiedName) {
			this.qualifiedName = qualifiedName;
		}

		public String getQualifiedName() {
			return qualifiedName;
		}

		public DocsAnnotation setQualifiedName(String qualifiedName) {
			this.qualifiedName = qualifiedName;
			return this;
		}
	}

	public static class DocsParameter {
		private String name;
		private String type;
		private String description;

		public DocsParameter() {
		}

		public String getName() {
			return name;
		}

		public DocsParameter setName(String name) {
			this.name = name;
			return this;
		}

		public String getType() {
			return type;
		}

		public DocsParameter setType(String type) {
			this.type = type;
			return this;
		}

		public String getDescription() {
			return description;
		}

		public DocsParameter setDescription(String description) {
			this.description = description;
			return this;
		}
	}

	private Kind kind;
	private String name;
	private String qualifiedName;
	private String packageName;
	private String visibility;
	private List<String> modifiers = new ArrayList<>();
	private List<DocsAnnotation> annotations = new ArrayList<>();
	private String description;
	private List<String> examples = new ArrayList<>();
	private String extendsType;
	private List<String> implementsTypes = new ArrayList<>();
	private List<DocsMember> fields = new ArrayList<>();
	private List<DocsMember> constructors = new ArrayList<>();
	private List<DocsMember> methods = new ArrayList<>();

	public DocsType() {
	}

	public Kind getKind() {
		return kind;
	}

	public DocsType setKind(Kind kind) {
		this.kind = kind;
		return this;
	}

	public String getName() {
		return name;
	}

	public DocsType setName(String name) {
		this.name = name;
		return this;
	}

	public String getQualifiedName() {
		return qualifiedName;
	}

	public DocsType setQualifiedName(String qualifiedName) {
		this.qualifiedName = qualifiedName;
		return this;
	}

	public String getPackageName() {
		return packageName;
	}

	public DocsType setPackageName(String packageName) {
		this.packageName = packageName;
		return this;
	}

	public String getVisibility() {
		return visibility;
	}

	public DocsType setVisibility(String visibility) {
		this.visibility = visibility;
		return this;
	}

	public List<String> getModifiers() {
		return modifiers;
	}

	public DocsType setModifiers(List<String> modifiers) {
		this.modifiers = modifiers;
		return this;
	}

	public List<DocsAnnotation> getAnnotations() {
		return annotations;
	}

	public DocsType setAnnotations(List<DocsAnnotation> annotations) {
		this.annotations = annotations;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public DocsType setDescription(String description) {
		this.description = description;
		return this;
	}

	public List<String> getExamples() {
		return examples;
	}

	public DocsType setExamples(List<String> examples) {
		this.examples = examples;
		return this;
	}

	public String getExtendsType() {
		return extendsType;
	}

	public DocsType setExtendsType(String extendsType) {
		this.extendsType = extendsType;
		return this;
	}

	public List<String> getImplementsTypes() {
		return implementsTypes;
	}

	public DocsType setImplementsTypes(List<String> implementsTypes) {
		this.implementsTypes = implementsTypes;
		return this;
	}

	public List<DocsMember> getFields() {
		return fields;
	}

	public DocsType setFields(List<DocsMember> fields) {
		this.fields = fields;
		return this;
	}

	public List<DocsMember> getConstructors() {
		return constructors;
	}

	public DocsType setConstructors(List<DocsMember> constructors) {
		this.constructors = constructors;
		return this;
	}

	public List<DocsMember> getMethods() {
		return methods;
	}

	public DocsType setMethods(List<DocsMember> methods) {
		this.methods = methods;
		return this;
	}
}
