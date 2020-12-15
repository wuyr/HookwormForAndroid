SKIPUNZIP=1

# extract verify.sh
ui_print "- Extracting verify.sh"
unzip -o "$ZIPFILE" 'verify.sh' -d "$TMPDIR" >&2
if [ ! -f "$TMPDIR/verify.sh" ]; then
  ui_print "*********************************************************"
  ui_print "! Unable to extract verify.sh!"
  ui_print "! This zip may be corrupted, please try downloading again"
  abort "*********************************************************"
fi
. $TMPDIR/verify.sh

# extract riru.sh
extract "$ZIPFILE" 'riru.sh' "$MODPATH"
. $MODPATH/riru.sh

check_riru_version
check_architecture

# extract libs
ui_print "- Extracting module files"

extract "$ZIPFILE" 'module.prop' "$MODPATH"
extract "$ZIPFILE" 'post-fs-data.sh' "$MODPATH"
extract "$ZIPFILE" 'uninstall.sh' "$MODPATH"

get_bit() {
  if [ "$(getprop ro.build.version.sdk)" -ge "30" ]; then
    search_result="$(find /data/app -name "$1*")"
    package_dir="$(basename "$(dirname "$search_result")")/$(basename "$search_result")"
    if [[ "$(dirname "$package_dir")" == "app" ]]; then
      package_dir="$(basename "$package_dir")"
    fi
  else
    package_dir="$(ls "/data/app" | grep "$1")"
  fi
  if [ -d "/data/app/$package_dir" ]; then
    lib_dir="/data/app/$package_dir/lib"
    oat_dir="/data/app/$package_dir/oat"
    abilist=$(getprop "ro.product.cpu.abilist")
    if [[ -d "$lib_dir/arm64" || -d "$lib_dir/x86_64" ]] || [[ -d "$oat_dir/arm64" || -d "$oat_dir/x86_64" ]]; then
      echo "64"
    elif [[ -d "$lib_dir/arm" || -d "$lib_dir/x86" ]] || [[ -d "$oat_dir/arm" || -d "$oat_dir/x86" ]]; then
      echo "32"
    elif [[ $abilist == *arm64* || $abilist == *x86_64* ]]; then
      echo "64"
    else
      echo "32"
    fi
  fi
}

process_32bit_libraries() {
  echo "processing 32 bit libraries..."
  lib=$(ls "$MODPATH/system/lib")
  for file in $lib; do
    if [ "$file" != "libriru_$RIRU_MODULE_ID.so" ] && [ "$file" != "libriru_$RIRU_MODULE_ID.so.sha256sum" ] && [ "${file##*.}" != "sha256sum" ]; then
      file="$MODPATH/system/lib/$file"
      target_process=$(grep_prop target_process_name "$TMPDIR/module.prop")
      target_process_list=${target_process//;/ }
      for package_name in $target_process_list; do
        bit="$(get_bit "$package_name")"
        if [ "$bit" == "32" ]; then
          target_so_dir="/data/data/$package_name/$library_path"
          ui_print "mkdir -p $target_so_dir"
          mkdir -p "$target_so_dir"
          ui_print "cp $file $target_so_dir"
          cp "$file" "$target_so_dir/"
        fi
      done
      ui_print "rm $file"
      rm "$file"
      ui_print "rm $file.sha256sum"
      rm "$file.sha256sum"
    fi
  done
}

process_64bit_libraries() {
  echo "processing 64 bit libraries..."
  lib=$(ls "$MODPATH/system/lib64")
  for file in $lib; do
    if [ "$file" != "libriru_$RIRU_MODULE_ID.so" ] && [ "$file" != "libriru_$RIRU_MODULE_ID.so.sha256sum" ] && [ "${file##*.}" != "sha256sum" ]; then
      file="$MODPATH/system/lib64/$file"
      target_process=$(grep_prop target_process_name "$TMPDIR/module.prop")
      target_process_list=${target_process//;/ }
      for package_name in $target_process_list; do
        bit="$(get_bit "$package_name")"
        if [ "$bit" == "64" ]; then
          target_so_dir="/data/data/$package_name/$library_path"
          ui_print "mkdir -p $target_so_dir"
          mkdir -p "$target_so_dir"
          ui_print "cp $file $target_so_dir"
          cp "$file" "$target_so_dir/"
        fi
      done
      ui_print "rm $file"
      rm "$file"
      ui_print "rm $file.sha256sum"
      rm "$file.sha256sum"
    fi
  done
}

library_path=$(grep_prop library_path "$TMPDIR/module.prop")

if [ "$ARCH" = "x86" ] || [ "$ARCH" = "x64" ]; then
  ui_print "- Extracting x86 libraries"
  extract_dir "$ZIPFILE" "system_x86/lib" "$MODPATH"
  mv "$MODPATH/system_x86/lib" "$MODPATH/system/lib"
  process_32bit_libraries

  if [ "$IS64BIT" = true ]; then
    ui_print "- Extracting x64 libraries"
    extract_dir "$ZIPFILE" "system_x86/lib64" "$MODPATH"
    mv "$MODPATH/system_x86/lib64" "$MODPATH/system/lib64"
    process_64bit_libraries
  fi
else
  ui_print "- Extracting arm libraries"
  extract_dir "$ZIPFILE" "system/lib" "$MODPATH"
  process_32bit_libraries

  if [ "$IS64BIT" = true ]; then
    ui_print "- Extracting arm64 libraries"
    extract_dir "$ZIPFILE" "system/lib64" "$MODPATH"
    process_64bit_libraries
  fi
fi

extract "$ZIPFILE" "extras.files" "$TMPDIR"

cat "$TMPDIR/extras.files" >&1 | while read file; do
  extract "$ZIPFILE" "$file" "$MODPATH"
done

set_perm_recursive "$MODPATH" 0 0 0755 0644

# extract Riru files
ui_print "- Extracting extra files"
[ -d "$RIRU_MODULE_PATH" ] || mkdir -p "$RIRU_MODULE_PATH" || abort "! Can't create $RIRU_MODULE_PATH"

rm -f "$RIRU_MODULE_PATH/module.prop.new"
extract "$ZIPFILE" 'riru/module.prop.new' "$RIRU_MODULE_PATH" true
set_perm "$RIRU_MODULE_PATH/module.prop.new" 0 0 0600 $RIRU_SECONTEXT
