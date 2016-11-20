#!/bin/bash

rm -rf zip-simple tar-simple
mkdir zip-simple tar-simple
for a in "" a1 a2; do
    for b in "" b1 b2; do
        for c in "" c1 c2; do
            rm -rf temp
            mkdir temp
            if [[ ! -z $a ]]; then
                cp samples/${a}.txt temp/a.txt
            fi
            if [[ ! -z $b ]]; then
                cp samples/${b}.txt temp/b.txt
            fi
            if [[ ! -z $c ]]; then
                mkdir -p temp/d
                cp samples/d/${c}.txt temp/d/c.txt
            fi

            zip -q zip-simple/${a}_${b}_${c}.zip -j temp temp/a.txt temp/b.txt temp/d/c.txt
            tar cf tar-simple/${a}_${b}_${c}.tar -C temp a.txt b.txt d/c.txt 2>/dev/null

            rm -rf temp
        done
    done
done


rm -rf recursive
mkdir recursive
for outer in zip tar; do
    for inner in zip tar; do
        for a in "" a1 a2; do
            for b in "" b1 b2; do
                for c in "" c1 c2; do
                    rm -rf temp
                    mkdir temp
                    if [[ ! -z $a ]]; then
                        cp samples/${a}.txt temp/a.txt
                    fi
                    if [[ ! -z $b || ! -z $c ]]; then
                        cp $inner-simple/_${b}_${c}.$inner temp/r.$inner
                    fi

                    case "$outer" in
                        "zip") zip -q recursive/${a}_r_${b}_${c}_$inner.zip -j temp temp/a.txt temp/r.$inner ;;
                        "tar") tar cf recursive/${a}_r_${b}_${c}_$inner.tar -C temp a.txt r.$inner 2>/dev/null ;;
                    esac

                    rm -rf temp
                done
            done
        done
    done
done
