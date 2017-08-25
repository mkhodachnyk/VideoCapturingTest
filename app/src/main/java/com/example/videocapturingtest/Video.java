package com.example.videocapturingtest;

import com.activeandroid.Model;
import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;

@Table(name = "Videos")
public class Video extends Model {
    @Column(name = "FileName", unique = true, onUniqueConflict = Column.ConflictAction.IGNORE)
    public String fileName;

    @Column(name = "IsUploaded")
    public Boolean isUploaded;

    public Video() {
        super();
    }

    public Video(String fileName, Boolean isUploaded) {
        super();
        this.fileName = fileName;
        this.isUploaded = isUploaded;
    }
}
