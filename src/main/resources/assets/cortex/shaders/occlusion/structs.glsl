//Required
//uint id;
//ivec3 offset;
//Bounding boxes
//uint baseOffset


struct Region {//32 bytes
    ivec4 id_start_count_x;//Count can be a uint16 or even possibly a uint8
    ivec4 y_z_sizexz_sizey_meta;//Size can be a half float
};

struct Section {//128 bytes //64 bytes should be possible

};

