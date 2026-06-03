package dev.jbang.docs;

import java.util.ArrayList;
import java.util.List;

public class DocsMember {

	public enum Kind {
		FIELD, CONSTRUCTOR, METHOD
	}

	private Kind kind;
	private String name;
	private String qualifiedName;
	private String visibility;
	private List<String> modifiers = new ArrayList<>();
	private List<DocsType.DocsAnnotation> annotations = new ArrayList<>();
	private String type;
	private List<DocsType.DocsParameter> parameters = new ArrayList<>();
	private List<String> throwsList = new ArrayList<>();
	private String description;
	private String returnDescription;

	public DocsMember() {
	}

	public Kind getKind() {
		return kind;
	}

	public DocsMember setKind(Kind kind) {
		this.kind = kind;
		return this;
	}

	public String getName() {
		return name;
	}

	public DocsMember setName(String name) {
		this.name = name;
		return this;
	}

	public String getQualifiedName() {
		return qualifiedName;
	}

	public DocsMember setQualifiedName(String qualifiedName) {
		this.qualifiedName = qualifiedName;
		return this;
	}

	public String getVisibility() {
		return visibility;
	}

	public DocsMember setVisibility(String visibility) {
		this.visibility = visibility;
		return this;
	}

	public List<String> getModifiers() {
		return modifiers;
	}

	public DocsMember setModifiers(List<String> modifiers) {
		this.modifiers = modifiers;
		return this;
	}

	public List<DocsType.DocsAnnotation> getAnnotations() {
		return annotations;
	}

	public DocsMember setAnnotations(List<DocsType.DocsAnnotation> annotations) {
		this.annotations = annotations;
		return this;
	}

	public String getType() {
		return type;
	}

	public DocsMember setType(String type) {
		this.type = type;
		return this;
	}

	public List<DocsType.DocsParameter> getParameters() {
		return parameters;
	}

	public DocsMember setParameters(List<DocsType.DocsParameter> parameters) {
		this.parameters = parameters;
		return this;
	}

	public List<String> getThrowsList() {
		return throwsList;
	}

	public DocsMember setThrowsList(List<String> throwsList) {
		this.throwsList = throwsList;
		return this;
	}

	public String getDescription() {
		return description;
	}

	public DocsMember setDescription(String description) {
		this.description = description;
		return this;
	}

	public String getReturnDescription() {
		return returnDescription;
	}

	public DocsMember setReturnDescription(String returnDescription) {
		this.returnDescription = returnDescription;
		return this;
	}
}
