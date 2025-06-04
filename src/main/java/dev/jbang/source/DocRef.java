package dev.jbang.source;

import java.util.Objects;

public class DocRef {

	private String id;
	private ResourceRef ref;

	public DocRef(String id, ResourceRef ref) {
		this.setId(id);
		this.setRef(ref);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public ResourceRef getRef() {
		return ref;
	}

	public void setRef(ResourceRef ref) {
		this.ref = ref;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		DocRef docRef = (DocRef) o;
		return Objects.equals(id, docRef.id) && Objects.equals(ref, docRef.ref);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, ref);
	}

	@Override
	public String toString() {
		return String.format("%s=%s", id, ref);
	}

	public static DocRef toDocRef(ResourceResolver siblingResolver, String repoReference) {
		String[] split = repoReference.split("=");
		String reporef = null;
		String repoid = null;

		if (split.length == 1) {
			reporef = split[0];
			repoid = reporef.toLowerCase();
		} else if (split.length == 2) {
			repoid = split[0];
			reporef = split[1];
		} else {
			// more than one =, fail for now. todo support escaping
			throw new IllegalStateException(
					"Invalid documentation reference, nomore than one = allowed: " + repoReference);
		}
		return new DocRef(repoid, siblingResolver.resolve(reporef));
	}
}
