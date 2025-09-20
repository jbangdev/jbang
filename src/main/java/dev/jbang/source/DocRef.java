package dev.jbang.source;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import dev.jbang.resources.ResourceRef;
import dev.jbang.resources.ResourceResolver;
import dev.jbang.source.parser.KeyValue;

public class DocRef {
	private final @Nullable String id;
	private final @NonNull ResourceRef ref;

	private DocRef(@Nullable String id, @NonNull ResourceRef ref) {
		this.id = id;
		this.ref = ref;
	}

	public @Nullable String getId() {
		return id;
	}

	public @NonNull ResourceRef getRef() {
		return ref;
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
		if (id == null) {
			return ref.toString();
		} else {
			return String.format("%s=%s", id, ref);
		}
	}

	public static DocRef create(@NonNull ResourceRef ref) {
		return new DocRef(null, ref);
	}

	public static DocRef create(@Nullable String id, @NonNull ResourceRef ref) {
		return new DocRef(id, ref);
	}

	public static DocRef toDocRef(ResourceResolver siblingResolver, KeyValue doc) {
		String docId;
		String docRef;
		if (doc.getValue() == null) {
			docId = null;
			docRef = doc.getKey();
		} else {
			docId = doc.getKey();
			docRef = doc.getValue();
		}
		ResourceRef ref = siblingResolver.resolve(docRef);
		return new DocRef(docId, ref != null ? ref
				: ResourceRef.forUnresolvable(docRef, "not resolvable from " + siblingResolver.description()));
	}

	public static DocRef toDocRef(ResourceResolver siblingResolver, String docId, String docRef) {
		ResourceRef ref = siblingResolver.resolve(docRef);
		return new DocRef(docId, ref != null ? ref
				: ResourceRef.forUnresolvable(docRef, "not resolvable from " + siblingResolver.description()));
	}
}
