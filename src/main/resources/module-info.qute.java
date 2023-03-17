module {moduleName} {
    {#for item in dependencies}
    requires {item};
    {/for}
    opens {packageName};
}
