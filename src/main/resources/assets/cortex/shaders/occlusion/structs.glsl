//Required
//uint id;
//ivec3 offset;
//Bounding boxes
//uint baseOffset



struct Section {//64 bytes
    ivec4 offsetAndId;

};


struct Region {//64 bytes
    ivec4 offsetAndId;
    ivec4 aabbAnd;
};