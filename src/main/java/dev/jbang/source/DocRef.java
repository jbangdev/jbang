package dev.jbang.source;

import java.util.Objects;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

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

	public static DocRef toDocRef(ResourceResolver siblingResolver, String repoReference) {
		String[] split = repoReference.split("=", 2);
		String docId;
		String docRef;
		if (split.length == 1) {
			docId = null;
			docRef = split[0];
		} else {
			docId = split[0];
			docRef = split[1];
		}
		ResourceRef ref = siblingResolver.resolve(docRef);
		return new DocRef(docId, ref != null ? ref : ResourceRef.forUnresolvable(docRef));
	}
}
