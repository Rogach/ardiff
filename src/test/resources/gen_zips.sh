#!/bin/bash

rm -rf zip-simple
mkdir zip-simple
for a in "" a1 a2 a3; do
    for b in "" b1 b2 b3; do
        for c in "" c1 c2 c3; do
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

            rm -rf temp
        done
    done
done


rm -rf zip-recursive
mkdir zip-recursive
for a in "" a1 a2 a3; do
    for b in "" b1 b2 b3; do
        for c in "" c1 c2 c3; do
            rm -rf temp
            mkdir temp
            if [[ ! -z $a ]]; then
                cp samples/${a}.txt temp/a.txt
            fi
            if [[ ! -z $b || ! -z $c ]]; then
                cp zip-simple/_${b}_${c}.zip temp/r.zip
            fi

            zip -q zip-recursive/${a}_r_${b}_${c}.zip -j temp temp/a.txt temp/r.zip

            rm -rf temp
        done
    done
done
