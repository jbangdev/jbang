function _in_path() { command -v "$1" >/dev/null 2>&1; }
_in_path jbang  || {
    export SCRATCH=`mktemp -d -t "$(basename $0).XXX"`
    echo "Downloading latest jbang from https://github.com/maxandersen/jbang"
    
    curl -s https://api.github.com/repos/maxandersen/jbang/releases/latest \
        | grep "browser_download_url.*.zip\"" | cut -d : -f 2,3 | tr -d \" \
        | xargs curl -s -L > $SCRATCH\jbang.zip
    unzip -q $SCRATCH\jbang.zip -d $SCRATCH
    mv $SCRATCH/jbang*/bin/jbang* /usr/local/bin
    chmod +x /usr/local/bin/jbang
    rm -rf $SCRATCH
}