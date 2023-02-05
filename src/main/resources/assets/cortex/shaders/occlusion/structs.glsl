struct Section {
    ivec4 offsetAndId;
    ivec4 boundingOffsetAndSizeBitvismsk;
};

ivec3 origin(Section section) {
    return section.offsetAndId.xyz;
}

uint id(Section section) {
    return uint(section.offsetAndId.w);
}

fpvec3 boundingOffset(Section section) {

}

fpvec3 boundingSize(Section section) {

}