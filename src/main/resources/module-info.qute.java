module {name} {
    {#for item in dependencies}
    requires {item};
    {/for}
}
