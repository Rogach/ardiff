#!/bin/bash

rm -rf zip-simple tar-simple tar.gz-simple tar.xz-simple ar-simple
mkdir zip-simple tar-simple tar.gz-simple tar.xz-simple ar-simple
for a in "" a1 a2; do
    for b in "" b1 b2; do
        for c in "" c1 c2; do
            rm -rf temp
            mkdir temp

            file_short_paths=""
            file_full_paths=""
            if [[ ! -z $a ]]; then
                cp samples/${a}.txt temp/a.txt
                file_short_paths="$file_short_paths a.txt"
                file_full_paths="$file_full_paths temp/a.txt"
            fi
            if [[ ! -z $b ]]; then
                cp samples/${b}.txt temp/b.txt
                file_short_paths="$file_short_paths b.txt"
                file_full_paths="$file_full_paths temp/b.txt"
            fi
            if [[ ! -z $c ]]; then
                mkdir -p temp/d
                cp samples/d/${c}.txt temp/d/c.txt
                file_short_paths="$file_short_paths d/c.txt"
                file_full_paths="$file_full_paths temp/d/c.txt"
            fi

            if [[ "$file_full_paths" != "" ]]; then
                zip -q zip-simple/${a}_${b}_${c}.zip -j temp $file_full_paths
                tar cf tar-simple/${a}_${b}_${c}.tar -C temp $file_short_paths
                tar czf tar.gz-simple/${a}_${b}_${c}.tar.gz -C temp $file_short_paths
                tar cJf tar.xz-simple/${a}_${b}_${c}.tar.xz -C temp $file_short_paths
                ar cr ar-simple/${a}_${b}_${c}.ar $file_full_paths
            fi

            rm -rf temp
        done
    done
done


rm -rf recursive
mkdir recursive
for outer in zip tar tar.gz tar.xz ar; do
    for inner in zip tar tar.gz tar.xz ar; do
        for a in "" a1 a2; do
            for b in "" b1 b2; do
                for c in "" c1 c2; do
                    rm -rf temp
                    mkdir temp

                    file_short_paths=""
                    file_full_paths=""
                    if [[ ! -z $a ]]; then
                        cp samples/${a}.txt temp/a.txt
                        file_short_paths="$file_short_paths a.txt"
                        file_full_paths="$file_full_paths temp/a.txt"
                    fi
                    if [[ ! -z $b || ! -z $c ]]; then
                        cp $inner-simple/_${b}_${c}.$inner temp/r.$inner
                        file_short_paths="$file_short_paths r.$inner"
                        file_full_paths="$file_full_paths temp/r.$inner"
                    fi

                    if [[ "$file_full_paths" != "" ]]; then
                        case "$outer" in
                            "zip") zip -q recursive/${a}_r_${b}_${c}_$inner.zip -j temp $file_full_paths ;;
                            "tar") tar cf recursive/${a}_r_${b}_${c}_$inner.tar -C temp $file_short_paths ;;
                            "tar.gz") tar czf recursive/${a}_r_${b}_${c}_$inner.tar.gz -C temp $file_short_paths ;;
                            "tar.xz") tar cJf recursive/${a}_r_${b}_${c}_$inner.tar.xz -C temp $file_short_paths ;;
                            "ar") ar cr recursive/${a}_r_${b}_${c}_$inner.ar $file_full_paths
                        esac
                    fi

                    rm -rf temp
                done
            done
        done
    done
done
