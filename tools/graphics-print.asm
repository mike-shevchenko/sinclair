; POKE 23875 with band 0..21; output is 176 bytes at 40000 + band * 176.

    org 23872

    jp convertLine

band:
    db 0

convertLine:
    push ix
    ld a, (band)
    and 0x18
    ld h, a
    ld a, (band)
    and 7
    rlca
    rlca
    rlca
    rlca
    rlca
    ld l, a
    ld a, h
    add a, 0x40
    ld h, a
    ld (bandBase), hl

    ld hl, 40000
    ld a, (band)
    or a
    jr z, outputReady
    ld bc, 176
addOutput:
    add hl, bc
    dec a
    jr nz, addOutput
outputReady:
    push hl
    pop ix

    xor a
    ld (pixelX), a
xLoop:
    ld a, (pixelX)
    and 7
    ld b, a
    ld a, 0x80
    jr z, maskReady
maskShift:
    rrca
    djnz maskShift
maskReady:
    ld e, a

    ld a, (pixelX)
    rrca
    rrca
    rrca
    and 0x1f
    add a, 10
    ld hl, (bandBase)
    add a, l
    ld l, a

    ld b, 8
    ld c, 0
    ld d, 0x80
rowLoop:
    ld a, (hl)
    and e
    jr z, noDot
    ld a, c
    or d
    ld c, a
noDot:
    inc h
    srl d
    djnz rowLoop

    ld a, c
    ld (ix + 0), a
    inc ix
    ld a, (pixelX)
    inc a
    ld (pixelX), a
    cp 176
    jr nz, xLoop

    pop ix
    ret

bandBase:
    dw 0
pixelX:
    db 0
