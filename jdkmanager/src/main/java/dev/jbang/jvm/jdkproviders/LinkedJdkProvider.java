package dev.jbang.jvm.jdkproviders;

import dev.jbang.jvm.Jdk;
import dev.jbang.jvm.util.FileUtils;
import org.jspecify.annotations.NonNull;

import java.nio.file.Path;
import java.util.Objects;

public class LinkedJdkProvider extends BaseFoldersJdkProvider {
    public static final String LINKED_ID = "linked";

    public LinkedJdkProvider(Path jdksRoot) {
        super(jdksRoot);
    }

    @Override
    protected boolean acceptFolder(Path jdkFolder) {
        return super.acceptFolder(jdkFolder) && !FileUtils.isLink(jdkFolder);
    }

    @Override
    public @NonNull Jdk install(@NonNull Jdk jdk) {
        Jdk existingJdk = getJdkById(jdk.getId());
        if (existingJdk != null && existingJdk.isInstalled() && !jdk.equals(existingJdk)) {
            uninstall(existingJdk);
        }
        Path linkedJdkPath = getJdkPath(jdk.getId());
        FileUtils.createLink(linkedJdkPath, jdk.getHome());
        return Objects.requireNonNull(createJdk(linkedJdkPath));
    }

    @Override
    public void uninstall(@NonNull Jdk jdk) {
        FileUtils.deletePath(jdk.getHome());
    }
}
