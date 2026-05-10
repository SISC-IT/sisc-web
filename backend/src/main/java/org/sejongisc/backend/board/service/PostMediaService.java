package org.sejongisc.backend.board.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.sejongisc.backend.board.dto.PostMediaResponse;
import org.sejongisc.backend.board.entity.Post;
import org.sejongisc.backend.board.entity.PostMedia;
import org.sejongisc.backend.board.entity.PostMediaType;
import org.sejongisc.backend.board.repository.PostMediaRepository;
import org.sejongisc.backend.common.exception.CustomException;
import org.sejongisc.backend.common.exception.ErrorCode;
import org.sejongisc.backend.user.entity.User;
import org.sejongisc.backend.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class PostMediaService {

  private final PostMediaRepository postMediaRepository;
  private final UserRepository userRepository;
  private final FileUploadService fileUploadService;

  @Transactional
  public PostMediaResponse uploadImage(MultipartFile file, UUID userId) {
    User uploadedBy = getUser(userId);
    FileUploadService.StoredFile storedFile = fileUploadService.storeImage(file);
    PostMedia media = saveMedia(storedFile, uploadedBy, PostMediaType.INLINE_IMAGE);
    return toResponse(media);
  }

  @Transactional
  public PostMediaResponse uploadFile(MultipartFile file, UUID userId) {
    User uploadedBy = getUser(userId);
    FileUploadService.StoredFile storedFile = fileUploadService.storeFile(file);
    PostMedia media = saveMedia(storedFile, uploadedBy, PostMediaType.FILE_ATTACHMENT);
    return toResponse(media);
  }

  @Transactional
  public void replacePostMedia(Post post, UUID userId, List<UUID> inlineMediaIds, List<UUID> attachmentIds) {
    List<UUID> requestedInlineIds = safeList(inlineMediaIds);
    List<UUID> requestedAttachmentIds = safeList(attachmentIds);
    Set<UUID> requestedIds = new HashSet<>();
    requestedIds.addAll(requestedInlineIds);
    requestedIds.addAll(requestedAttachmentIds);

    List<PostMedia> existingMedia = postMediaRepository.findAllByPostPostId(post.getPostId());
    for (PostMedia media : existingMedia) {
      if (!requestedIds.contains(media.getMediaId())) {
        fileUploadService.delete(media.getSavedFilename());
        postMediaRepository.delete(media);
      }
    }

    attachRequestedMedia(post, userId, requestedInlineIds, PostMediaType.INLINE_IMAGE);
    attachRequestedMedia(post, userId, requestedAttachmentIds, PostMediaType.FILE_ATTACHMENT);
  }

  @Transactional
  public void deleteAllByPost(UUID postId) {
    List<PostMedia> existingMedia = postMediaRepository.findAllByPostPostId(postId);
    for (PostMedia media : existingMedia) {
      fileUploadService.delete(media.getSavedFilename());
    }
    postMediaRepository.deleteAllByPostPostId(postId);
  }

  @Transactional(readOnly = true)
  public List<PostMediaResponse> getPostMedia(UUID postId, PostMediaType mediaType) {
    return postMediaRepository.findAllByPostPostIdAndMediaTypeOrderBySortOrderAscCreatedDateAsc(postId, mediaType)
        .stream()
        .map(this::toResponse)
        .toList();
  }

  private PostMedia saveMedia(FileUploadService.StoredFile storedFile, User uploadedBy, PostMediaType mediaType) {
    PostMedia media = PostMedia.builder()
        .uploadedBy(uploadedBy)
        .mediaType(mediaType)
        .savedFilename(storedFile.savedFilename())
        .originalFilename(storedFile.originalFilename())
        .filePath(storedFile.filePath())
        .publicPath(storedFile.publicPath())
        .contentType(storedFile.contentType())
        .fileSize(storedFile.fileSize())
        .width(storedFile.width())
        .height(storedFile.height())
        .build();
    return postMediaRepository.save(media);
  }

  private void attachRequestedMedia(Post post, UUID userId, List<UUID> mediaIds, PostMediaType mediaType) {
    if (mediaIds.isEmpty()) {
      return;
    }

    Map<UUID, PostMedia> mediaById = postMediaRepository.findAllByMediaIdIn(mediaIds)
        .stream()
        .collect(Collectors.toMap(PostMedia::getMediaId, Function.identity()));

    List<PostMedia> mediaToSave = new ArrayList<>();
    for (int i = 0; i < mediaIds.size(); i++) {
      UUID mediaId = mediaIds.get(i);
      PostMedia media = mediaById.get(mediaId);
      if (media == null
          || media.getUploadedBy() == null
          || !Objects.equals(media.getUploadedBy().getUserId(), userId)
          || media.getMediaType() != mediaType) {
        throw new CustomException(ErrorCode.POST_MEDIA_NOT_FOUND);
      }
      if (media.getPost() != null && !Objects.equals(media.getPost().getPostId(), post.getPostId())) {
        throw new CustomException(ErrorCode.POST_MEDIA_NOT_FOUND);
      }
      media.setPost(post);
      media.setSortOrder(i);
      mediaToSave.add(media);
    }

    postMediaRepository.saveAll(mediaToSave);
  }

  private PostMediaResponse toResponse(PostMedia media) {
    return PostMediaResponse.of(media, fileUploadService.buildPublicUrl(media.getPublicPath()));
  }

  private User getUser(UUID userId) {
    return userRepository.findById(userId)
        .orElseThrow(() -> new CustomException(ErrorCode.USER_NOT_FOUND));
  }

  private List<UUID> safeList(List<UUID> ids) {
    if (ids == null) {
      return List.of();
    }
    return ids.stream()
        .filter(Objects::nonNull)
        .toList();
  }
}
